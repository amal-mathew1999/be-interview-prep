package com.example.mockretest.library;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mockretest.library.dto.BookRequest;
import com.example.mockretest.library.dto.BookResponse;
import com.example.mockretest.library.dto.LoanResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(LibraryController.class)
class LibraryControllerTest {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock libraryClock() {
            return Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibraryService service;

    private static String bookJson(String title, String author, String isbn, Integer year) {
        return """
                {"title": %s, "author": %s, "isbn": %s, "publishedYear": %s}
                """
                .formatted(quote(title), quote(author), quote(isbn), year);
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions postBook(String json) throws Exception {
        return mockMvc.perform(
                post("/api/books").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static ResultActions expectProblem(ResultActions actions, int status, String instance) throws Exception {
        actions.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(instance));
        return actions;
    }

    @Test
    void createsBookAndReturns201WithLocation() throws Exception {
        when(service.create(new BookRequest("Dune", "Frank Herbert", "123", 1965)))
                .thenReturn(new BookResponse(5L, "Dune", "Frank Herbert", "123", 1965, true));

        postBook(bookJson("Dune", "Frank Herbert", "123", 1965))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Dune"))
                .andExpect(jsonPath("$.author").value("Frank Herbert"))
                .andExpect(jsonPath("$.isbn").value("123"))
                .andExpect(jsonPath("$.publishedYear").value(1965))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void acceptsCurrentYearAndMissingYear() throws Exception {
        when(service.create(any())).thenReturn(new BookResponse(1L, "T", "A", "1", null, true));

        postBook(bookJson("T", "A", "1", 2026)).andExpect(status().isCreated());
        postBook(bookJson("T", "A", "1", null)).andExpect(status().isCreated());
    }

    @Test
    void returns400WithErrorsForBlankTitle() throws Exception {
        ResultActions result = postBook(bookJson("  ", "Frank Herbert", "123", 1965));

        expectProblem(result, 400, "/api/books");
        result.andExpect(jsonPath("$.type").value("urn:problem-type:library:validation-error"))
                .andExpect(jsonPath("$.errors.title").isNotEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void returns400WithErrorsForMissingRequiredFields() throws Exception {
        ResultActions result = postBook("{}");

        expectProblem(result, 400, "/api/books");
        result.andExpect(jsonPath("$.errors.title").isNotEmpty())
                .andExpect(jsonPath("$.errors.author").isNotEmpty())
                .andExpect(jsonPath("$.errors.isbn").isNotEmpty());
    }

    @Test
    void returns400WithErrorsForFuturePublishedYear() throws Exception {
        ResultActions result = postBook(bookJson("Dune", "Frank Herbert", "123", 2027));

        expectProblem(result, 400, "/api/books");
        result.andExpect(jsonPath("$.errors.publishedYear").isNotEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void returns400ForFutureYearOnUpdate() throws Exception {
        ResultActions result = mockMvc.perform(put("/api/books/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookJson("Dune", "Frank Herbert", "123", 2027)));

        expectProblem(result, 400, "/api/books/1");
        result.andExpect(jsonPath("$.errors.publishedYear").isNotEmpty());
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        expectProblem(postBook("{\"title\": "), 400, "/api/books");
    }

    @Test
    void returns400ForNonNumericId() throws Exception {
        expectProblem(mockMvc.perform(get("/api/books/abc")), 400, "/api/books/abc");
    }

    @Test
    void returns404ForUnknownBook() throws Exception {
        when(service.get(9L)).thenThrow(new BookNotFoundException(9L));

        expectProblem(mockMvc.perform(get("/api/books/9")), 404, "/api/books/9")
                .andExpect(jsonPath("$.type").value("urn:problem-type:library:not-found"));
    }

    @Test
    void returns409ForDuplicateIsbn() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateIsbnException("123"));

        expectProblem(postBook(bookJson("Dune", "Frank Herbert", "123", 1965)), 409, "/api/books");
    }

    @Test
    void returns409WhenDeletingBorrowedBook() throws Exception {
        doThrow(new BookUnavailableException(3L)).when(service).delete(3L);

        ResultActions result = mockMvc.perform(delete("/api/books/3"));

        expectProblem(result, 409, "/api/books/3");
    }

    @Test
    void deletesBookAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/books/3")).andExpect(status().isNoContent());
    }

    @Test
    void listsBooksWithFilters() throws Exception {
        when(service.search("dun", "herb"))
                .thenReturn(List.of(new BookResponse(1L, "Dune", "Frank Herbert", "123", 1965, true)));

        mockMvc.perform(get("/api/books").param("title", "dun").param("author", "herb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Dune"));
    }

    @Test
    void borrowsBookAndReturnsLoan() throws Exception {
        when(service.borrow(1L, "m-1"))
                .thenReturn(new LoanResponse(7L, 1L, "m-1", Instant.parse("2026-01-01T10:00:00Z"), null));

        mockMvc.perform(post("/api/books/1/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": \"m-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(7))
                .andExpect(jsonPath("$.bookId").value(1))
                .andExpect(jsonPath("$.memberId").value("m-1"))
                .andExpect(jsonPath("$.borrowedAt").value("2026-01-01T10:00:00Z"));
    }

    @Test
    void returns400ForBlankOrMissingMemberId() throws Exception {
        for (String body : List.of("{\"memberId\": \" \"}", "{}")) {
            ResultActions result = mockMvc.perform(post("/api/books/1/borrow")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
            expectProblem(result, 400, "/api/books/1/borrow");
            result.andExpect(jsonPath("$.errors.memberId").isNotEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void returns409WithClearDetailWhenBorrowingBorrowedBook() throws Exception {
        when(service.borrow(eq(1L), anyString())).thenThrow(new BookUnavailableException(1L));

        ResultActions result = mockMvc.perform(post("/api/books/1/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"m-2\"}"));

        expectProblem(result, 409, "/api/books/1/borrow");
        result.andExpect(jsonPath("$.detail").value(containsString("currently borrowed")));
    }

    @Test
    void returns409WhenReturningBookThatIsNotBorrowed() throws Exception {
        when(service.returnBook(anyLong())).thenThrow(new BookNotBorrowedException(1L));

        expectProblem(mockMvc.perform(post("/api/books/1/return")), 409, "/api/books/1/return");
    }

    @Test
    void returns500WithGenericDetailForUnexpectedErrors() throws Exception {
        when(service.get(1L)).thenThrow(new IllegalStateException("secret internal failure"));

        ResultActions result = mockMvc.perform(get("/api/books/1"));

        expectProblem(result, 500, "/api/books/1");
        result.andExpect(jsonPath("$.detail").value(not(containsString("secret"))));
    }
}
