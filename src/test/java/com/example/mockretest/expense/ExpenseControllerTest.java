package com.example.mockretest.expense;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mockretest.expense.dto.ExpenseResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    private static String body(String amount, String category, String date) {
        return """
                {"amount": %s, "category": %s, "date": %s, "note": "lunch"}
                """
                .formatted(amount, category, date);
    }

    private ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(
                post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static ExpenseResponse sampleResponse() {
        return new ExpenseResponse(
                7L, new BigDecimal("12.50"), ExpenseCategory.FOOD, LocalDate.of(2026, 3, 1), "lunch");
    }

    @Test
    void createsExpenseAndReturns201WithLocation() throws Exception {
        given(expenseService.create(any())).willReturn(sampleResponse());

        postJson(body("12.5", "\"food\"", "\"2026-03-01\""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/expenses/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.date").value("2026-03-01"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amount\":12.50")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "1.234"})
    void rejectsInvalidAmountWith400(String amount) throws Exception {
        postJson(body(amount, "\"FOOD\"", "\"2026-03-01\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/expenses"))
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsUnknownCategoryWith400() throws Exception {
        postJson(body("5.00", "\"CLOTHES\"", "\"2026-03-01\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.category").exists());
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsMissingRequiredFieldsWith400() throws Exception {
        postJson("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.category").exists())
                .andExpect(jsonPath("$.errors.date").exists());
    }

    @Test
    void rejectsTooLongNoteWith400() throws Exception {
        String json =
                """
                {"amount": 1.00, "category": "FOOD", "date": "2026-03-01", "note": "%s"}
                """
                        .formatted("x".repeat(501));
        postJson(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").exists());
    }

    @Test
    void rejectsMalformedJsonWithProblemDetail() throws Exception {
        postJson("{\"amount\": ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/expenses"));
    }

    @Test
    void rejectsInvalidDateWithProblemDetail() throws Exception {
        postJson(body("5.00", "\"FOOD\"", "\"2026-02-30\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        postJson(body("5.00", "\"FOOD\"", "\"03/01/2026\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void returnsExpenseById() throws Exception {
        given(expenseService.get(7L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/expenses/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void returns404WhenExpenseMissingOnGet() throws Exception {
        given(expenseService.get(99L)).willThrow(new ExpenseNotFoundException(99L));

        mockMvc.perform(get("/api/expenses/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/expenses/99"));
    }

    @Test
    void returns404WhenExpenseMissingOnPut() throws Exception {
        given(expenseService.update(eq(99L), any())).willThrow(new ExpenseNotFoundException(99L));

        mockMvc.perform(put("/api/expenses/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("5.00", "\"FOOD\"", "\"2026-03-01\"")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void returns404WhenExpenseMissingOnDelete() throws Exception {
        org.mockito.BDDMockito.willThrow(new ExpenseNotFoundException(99L))
                .given(expenseService)
                .delete(99L);

        mockMvc.perform(delete("/api/expenses/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void updatesExpenseAndReturns200() throws Exception {
        given(expenseService.update(eq(7L), any())).willReturn(sampleResponse());

        mockMvc.perform(put("/api/expenses/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("12.50", "\"Food\"", "\"2026-03-01\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void rejectsInvalidUpdateWith400() throws Exception {
        mockMvc.perform(put("/api/expenses/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1.234", "\"FOOD\"", "\"2026-03-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(expenseService);
    }

    @Test
    void deletesExpenseAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/expenses/7")).andExpect(status().isNoContent());
    }

    @Test
    void listsExpensesWithFilters() throws Exception {
        given(expenseService.list(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "food"))
                .willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/expenses")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("category", "food"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));
    }

    @Test
    void returns400WhenFromAfterTo() throws Exception {
        given(expenseService.list(any(), any(), any()))
                .willThrow(new InvalidExpenseQueryException("'from' must not be after 'to'"));

        mockMvc.perform(get("/api/expenses").param("from", "2026-04-01").param("to", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
    }

    @Test
    void returns400ForInvalidDateFilter() throws Exception {
        mockMvc.perform(get("/api/expenses").param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void returnsSummary() throws Exception {
        given(expenseService.summary(YearMonth.of(2026, 3)))
                .willReturn(new com.example.mockretest.expense.dto.ExpenseSummaryResponse(
                        "2026-03", java.util.Map.of("FOOD", new BigDecimal("0.30")), new BigDecimal("0.30")));

        mockMvc.perform(get("/api/expenses/summary").param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-03"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"overall\":0.30")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "march", "2026-3-01", ""})
    void returns400ForInvalidMonth(String month) throws Exception {
        mockMvc.perform(get("/api/expenses/summary").param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        verifyNoInteractions(expenseService);
    }

    @Test
    void returns400ForMissingMonth() throws Exception {
        mockMvc.perform(get("/api/expenses/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void returns500ProblemWithoutInternalDetails() throws Exception {
        given(expenseService.get(1L)).willThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/expenses/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(
                        content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
