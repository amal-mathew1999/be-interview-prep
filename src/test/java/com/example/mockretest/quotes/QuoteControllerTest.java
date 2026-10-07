package com.example.mockretest.quotes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mockretest.ratelimit.RateLimitConfig;
import com.example.mockretest.ratelimit.RateLimitInterceptor;
import com.example.mockretest.ratelimit.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
@Import({RateLimitConfig.class, RateLimitInterceptor.class, RateLimitService.class, QuoteService.class})
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRandomQuoteWith200() throws Exception {
        mockMvc.perform(get("/api/quotes/random").header("X-API-Key", "quote-controller-test"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").isNotEmpty())
                .andExpect(jsonPath("$.author").isNotEmpty());
    }

    @Test
    void returns401WhenApiKeyMissing() throws Exception {
        mockMvc.perform(get("/api/quotes/random")).andExpect(status().isUnauthorized());
    }
}
