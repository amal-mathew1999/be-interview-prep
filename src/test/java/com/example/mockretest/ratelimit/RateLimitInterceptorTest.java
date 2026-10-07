package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RateLimitInterceptorTest {

    private final JsonMapper jsonMapper = new JsonMapper();
    private final RateLimitProperties properties =
            new RateLimitProperties(10, Duration.ofMinutes(1), Duration.ofMinutes(1), 128, 1);
    private final RateLimitService service = new RateLimitService(
            properties, new RateLimitClock(new RateLimitTestClock(Instant.parse("2026-01-01T00:00:00Z"))));
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(service, properties, jsonMapper);

    @Test
    void newKeyAtTrackedKeyCapIsRejectedWith503ProblemAndRetryAfter() throws Exception {
        assertThat(handle("tracked").getStatus()).isEqualTo(200);

        MockHttpServletResponse response = handle("one-too-many");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isNull();
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("type").asString()).isEqualTo("urn:problem-type:ratelimit:capacity-exceeded");
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("detail").asString())
                .isEqualTo("Rate limiter is tracking too many API keys; retry in 1 second.");
        assertThat(service.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void trackedKeyIsStillServedAtTrackedKeyCap() throws Exception {
        handle("tracked");

        MockHttpServletResponse response = handle("tracked");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("8");
    }

    private MockHttpServletResponse handle(String apiKey) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/quotes/random");
        request.addHeader("X-API-Key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        return response;
    }
}
