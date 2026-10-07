package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    private static final String RANDOM_QUOTE = "/api/quotes/random";
    private static final String API_KEY_HEADER = "X-API-Key";

    @TestBean(name = "rateLimitClock")
    private Clock rateLimitClock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitProperties properties;

    static Clock rateLimitClock() {
        return new RateLimitTestClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void usesDefaultLimitAndWindowFromProperties() {
        assertThat(properties.limit()).isEqualTo(10);
        assertThat(properties.window()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void allowedResponseIncludesRateLimitHeaders() throws Exception {
        callWith(newKey())
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    @Test
    void eleventhRequestInAMinuteIsRejectedWith429AndRetryAfter() throws Exception {
        String key = newKey();
        for (int i = 0; i < 10; i++) {
            callWith(key).andExpect(status().isOk());
        }

        callWith(key)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value("Rate limit of 10 requests exceeded; retry in 60 seconds."))
                .andExpect(jsonPath("$.instance").value(RANDOM_QUOTE));
    }

    @Test
    void twoKeysAreIndependent() throws Exception {
        String exhausted = newKey();
        for (int i = 0; i < 10; i++) {
            callWith(exhausted).andExpect(status().isOk());
        }
        callWith(exhausted).andExpect(status().isTooManyRequests());

        callWith(newKey()).andExpect(status().isOk()).andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    @Test
    void keyMayRequestAgainAfterWindowPasses() throws Exception {
        String key = newKey();
        for (int i = 0; i < 10; i++) {
            callWith(key).andExpect(status().isOk());
        }
        callWith(key).andExpect(status().isTooManyRequests());

        ((RateLimitTestClock) rateLimitClock).advance(Duration.ofMinutes(1));

        callWith(key).andExpect(status().isOk()).andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    @Test
    void missingApiKeyReturns401ProblemJson() throws Exception {
        mockMvc.perform(get(RANDOM_QUOTE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "ApiKey header=\"X-API-Key\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Missing X-API-Key header."))
                .andExpect(jsonPath("$.instance").value(RANDOM_QUOTE))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void blankApiKeyReturns401() throws Exception {
        mockMvc.perform(get(RANDOM_QUOTE).header(API_KEY_HEADER, "   "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private ResultActions callWith(String apiKey) throws Exception {
        return mockMvc.perform(get(RANDOM_QUOTE).header(API_KEY_HEADER, apiKey));
    }

    private static String newKey() {
        return "key-" + UUID.randomUUID();
    }
}
