package com.example.mockretest.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.ServletWebRequest;

@WebMvcTest(QuoteController.class)
class QuotesExceptionHandlerTest {

    private static final String SECRET = "jdbc:postgresql://db.internal/secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void unexpectedExceptionReturnsGeneric500ProblemWithoutInternalDetails() throws Exception {
        given(quoteService.randomQuote()).willThrow(new IllegalStateException(SECRET));

        MvcResult result = mockMvc.perform(get("/api/quotes/random"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:quotes:internal-error"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.instance").value("/api/quotes/random"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(SECRET)
                .doesNotContain("IllegalStateException")
                .doesNotContain("trace");
    }

    @Test
    void frameworkProblemsGetExplicitType() throws Exception {
        QuotesExceptionHandler handler = new QuotesExceptionHandler();
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/api/quotes/random"));

        ResponseEntity<Object> response = handler.handleException(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody())
                .isInstanceOfSatisfying(ProblemDetail.class, problem -> assertThat(problem.getType())
                        .isEqualTo(URI.create("urn:problem-type:quotes:http-406")));
    }
}
