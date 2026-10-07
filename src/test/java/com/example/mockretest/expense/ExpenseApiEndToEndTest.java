package com.example.mockretest.expense;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseApiEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private String create(String amount, String category, String date) throws Exception {
        String json = """
                {"amount": %s, "category": "%s", "date": "%s"}
                """
                .formatted(amount, category, date);
        return mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
    }

    @Test
    void summaryAddsTenAndTwentyCentsToExactlyThirtyCents() throws Exception {
        create("0.10", "food", "2026-03-05");
        create("0.20", "FOOD", "2026-03-06");

        mockMvc.perform(get("/api/expenses/summary").param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .json(
                                        """
                        {"month":"2026-03","totals":{"FOOD":0.30,"TRAVEL":0.00,"BILLS":0.00,"OTHER":0.00},"overall":0.30}
                        """))
                .andExpect(content().string(containsString("\"FOOD\":0.30")))
                .andExpect(content().string(containsString("\"TRAVEL\":0.00")))
                .andExpect(content().string(containsString("\"overall\":0.30")));
    }

    @Test
    void summaryIncludesMonthBoundariesOnlyInLeapFebruary() throws Exception {
        create("1.00", "OTHER", "2028-01-31");
        create("2.00", "OTHER", "2028-02-01");
        create("4.00", "OTHER", "2028-02-29");
        create("8.00", "OTHER", "2028-03-01");

        mockMvc.perform(get("/api/expenses/summary").param("month", "2028-02"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"OTHER\":6.00")))
                .andExpect(content().string(containsString("\"overall\":6.00")));
    }

    @Test
    void summaryIncludesMonthBoundariesInThirtyOneDayMonth() throws Exception {
        create("1.00", "TRAVEL", "2026-02-28");
        create("2.00", "TRAVEL", "2026-03-01");
        create("4.00", "TRAVEL", "2026-03-31");
        create("8.00", "TRAVEL", "2026-04-01");

        mockMvc.perform(get("/api/expenses/summary").param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"TRAVEL\":6.00")));
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        String location = create("12.5", "bills", "2026-03-01");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BILLS"))
                .andExpect(content().string(containsString("\"amount\":12.50")));

        mockMvc.perform(put(location)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 3, \"category\": \"Other\", \"date\": \"2026-03-02\", \"note\": \"n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("OTHER"))
                .andExpect(jsonPath("$.note").value("n"))
                .andExpect(content().string(containsString("\"amount\":3.00")));

        mockMvc.perform(get("/api/expenses").param("category", "other").param("from", "2026-03-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isNotFound());
        mockMvc.perform(delete(location)).andExpect(status().isNotFound());
    }

    @Test
    void listRejectsFromAfterTo() throws Exception {
        mockMvc.perform(get("/api/expenses").param("from", "2026-04-01").param("to", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void listRejectsUnknownCategoryFilter() throws Exception {
        mockMvc.perform(get("/api/expenses").param("category", "CLOTHES"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
