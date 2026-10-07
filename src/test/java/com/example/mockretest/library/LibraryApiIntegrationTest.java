package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end tests against the real persistence layer (H2). Each test uses unique ISBNs and titles. */
@SpringBootTest
@AutoConfigureMockMvc
class LibraryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static String uniqueIsbn() {
        return "isbn-" + UUID.randomUUID().toString().substring(0, 20);
    }

    private static String bookJson(String title, String author, String isbn, Integer year) {
        return """
                {"title": "%s", "author": "%s", "isbn": "%s", "publishedYear": %s}
                """
                .formatted(title, author, isbn, year);
    }

    private ResultActions postBook(String json) throws Exception {
        return mockMvc.perform(
                post("/api/books").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createBook(String title, String author, String isbn) throws Exception {
        MvcResult result = postBook(bookJson(title, author, isbn, 2001))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private ResultActions borrow(long id, String memberId) throws Exception {
        return mockMvc.perform(post("/api/books/{id}/borrow", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"" + memberId + "\"}"));
    }

    @Test
    void createsBookAndFetchesIt() throws Exception {
        String isbn = uniqueIsbn();
        long id = createBook("Neuromancer", "William Gibson", isbn);

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Neuromancer"))
                .andExpect(jsonPath("$.author").value("William Gibson"))
                .andExpect(jsonPath("$.isbn").value(isbn))
                .andExpect(jsonPath("$.publishedYear").value(2001))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void createReturnsRelativeLocationHeader() throws Exception {
        postBook(bookJson("Loc", "Author", uniqueIsbn(), null))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/books/\\d+")));
    }

    @Test
    void rejectsFuturePublishedYear() throws Exception {
        int nextYear = Year.now().getValue() + 1;

        postBook(bookJson("Future", "Author", uniqueIsbn(), nextYear))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.publishedYear").isNotEmpty());
    }

    @Test
    void returns404ForUnknownIdOnEveryEndpoint() throws Exception {
        long unknown = Long.MAX_VALUE;
        mockMvc.perform(get("/api/books/{id}", unknown)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/books/{id}", unknown)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson("T", "A", uniqueIsbn(), null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/books/{id}", unknown)).andExpect(status().isNotFound());
        borrow(unknown, "m").andExpect(status().isNotFound());
        mockMvc.perform(post("/api/books/{id}/return", unknown))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsDuplicateIsbnOnCreateAndUpdate() throws Exception {
        String isbn = uniqueIsbn();
        createBook("First", "Author", isbn);
        long second = createBook("Second", "Author", uniqueIsbn());

        postBook(bookJson("Copy", "Author", isbn, null))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(put("/api/books/{id}", second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson("Second", "Author", isbn, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void updatesBookKeepingOwnIsbn() throws Exception {
        String isbn = uniqueIsbn();
        long id = createBook("Old title", "Old author", isbn);

        mockMvc.perform(put("/api/books/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson("New title", "New author", isbn, 1999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.author").value("New author"))
                .andExpect(jsonPath("$.publishedYear").value(1999));
    }

    @Test
    void searchesByTitleAndAuthorCaseInsensitively() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        createBook("The Hobbit " + tag, "J.R.R. Tolkien", uniqueIsbn());
        createBook("The Silmarillion " + tag, "J.R.R. Tolkien", uniqueIsbn());
        createBook("Hobbit Fan Guide " + tag, "Someone Else", uniqueIsbn());

        mockMvc.perform(get("/api/books").param("title", "HOBBIT " + tag.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("The Hobbit " + tag));
        mockMvc.perform(get("/api/books").param("title", tag).param("author", "tolkien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/books").param("title", "hobbit").param("author", "else"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Hobbit Fan Guide " + tag + "')]")
                        .exists());
    }

    @Test
    void borrowReturnFlow() throws Exception {
        long id = createBook("Lendable", "Author", uniqueIsbn());

        borrow(id, "member-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").isNumber())
                .andExpect(jsonPath("$.bookId").value(id))
                .andExpect(jsonPath("$.memberId").value("member-1"))
                .andExpect(jsonPath("$.borrowedAt").isNotEmpty());
        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(jsonPath("$.available").value(false));

        borrow(id, "member-2")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("currently borrowed")));

        mockMvc.perform(delete("/api/books/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("currently borrowed")));

        mockMvc.perform(post("/api/books/{id}/return", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value("member-1"))
                .andExpect(jsonPath("$.returnedAt").isNotEmpty());
        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(jsonPath("$.available").value(true));

        mockMvc.perform(post("/api/books/{id}/return", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        borrow(id, "member-2").andExpect(status().isOk());
        mockMvc.perform(post("/api/books/{id}/return", id)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/books/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/books/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void exactlyOneOfConcurrentBorrowsSucceeds() throws Exception {
        long id = createBook("Contended", "Author", uniqueIsbn());
        List<Integer> statuses = runConcurrently(
                8, i -> borrow(id, "member-" + i).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void exactlyOneOfConcurrentCreatesWithSameIsbnSucceeds() throws Exception {
        String isbn = uniqueIsbn();
        List<Integer> statuses = runConcurrently(8, i -> postBook(bookJson("Racer " + i, "Author", isbn, null))
                .andReturn()
                .getResponse()
                .getStatus());

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
    }

    @FunctionalInterface
    private interface IndexedCall {
        int call(int index) throws Exception;
    }

    private static List<Integer> runConcurrently(int threads, IndexedCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Integer> task = () -> {
                    start.await();
                    return call.call(index);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
