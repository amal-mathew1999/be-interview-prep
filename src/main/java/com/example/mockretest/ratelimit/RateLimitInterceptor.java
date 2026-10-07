package com.example.mockretest.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Identifies clients by the {@code X-API-Key} header and enforces the per-key rate limit, answering with RFC 9457
 * problem+json when the key is missing ({@code 401}) or the limit is exceeded ({@code 429}).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final RateLimitService rateLimitService;
    private final JsonMapper jsonMapper;

    public RateLimitInterceptor(RateLimitService rateLimitService, JsonMapper jsonMapper) {
        this.rateLimitService = rateLimitService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Missing " + API_KEY_HEADER + " header.");
            return false;
        }

        RateLimitDecision decision = rateLimitService.tryAcquire(apiKey.strip());
        response.setHeader(LIMIT_HEADER, Integer.toString(decision.limit()));
        response.setHeader(REMAINING_HEADER, Integer.toString(decision.remaining()));
        if (decision.allowed()) {
            return true;
        }

        long retryAfterSeconds = decision.retryAfterSeconds();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        String detail = "Rate limit of %d requests exceeded; retry in %d %s."
                .formatted(decision.limit(), retryAfterSeconds, retryAfterSeconds == 1 ? "second" : "seconds");
        writeProblem(request, response, HttpStatus.TOO_MANY_REQUESTS, detail);
        return false;
    }

    private void writeProblem(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
