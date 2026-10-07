package com.example.mockretest.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
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
    static final String AUTHENTICATE_CHALLENGE = "ApiKey header=\"" + API_KEY_HEADER + "\"";

    private final RateLimitService rateLimitService;
    private final JsonMapper jsonMapper;

    public RateLimitInterceptor(RateLimitService rateLimitService, JsonMapper jsonMapper) {
        this.rateLimitService = rateLimitService;
        this.jsonMapper = jsonMapper
                .rebuild()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATE_CHALLENGE);
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        instanceOf(request).ifPresent(problem::setInstance);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }

    private static Optional<URI> instanceOf(HttpServletRequest request) {
        try {
            return Optional.of(URI.create(request.getRequestURI()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
