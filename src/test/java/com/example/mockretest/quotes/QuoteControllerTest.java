package com.example.mockretest.quotes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Controller slice only; API-key and rate-limit behavior is covered by {@code RateLimitIntegrationTest}. */
@WebMvcTest(QuoteController.class)
@Import(QuoteService.class)
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRandomQuoteWith200() throws Exception {
        mockMvc.perform(get("/api/quotes/random"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").isNotEmpty())
                .andExpect(jsonPath("$.author").isNotEmpty());
    }
}
