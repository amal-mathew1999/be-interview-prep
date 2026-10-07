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
 * problem+json when the key is missing or invalid ({@code 401}) or the limit is exceeded ({@code 429}).
 *
 * <p>Keys longer than {@code ratelimit.max-api-key-length} are rejected before being tracked, so clients cannot grow
 * the limiter's memory with arbitrarily large keys.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    static final String AUTHENTICATE_CHALLENGE = "ApiKey header=\"" + API_KEY_HEADER + "\"";
    static final URI MISSING_API_KEY_TYPE = URI.create("urn:problem-type:ratelimit:missing-api-key");
    static final URI INVALID_API_KEY_TYPE = URI.create("urn:problem-type:ratelimit:invalid-api-key");
    static final URI TOO_MANY_REQUESTS_TYPE = URI.create("urn:problem-type:ratelimit:too-many-requests");

    private final RateLimitService rateLimitService;
    private final int maxApiKeyLength;
    private final JsonMapper jsonMapper;

    public RateLimitInterceptor(
            RateLimitService rateLimitService, RateLimitProperties properties, JsonMapper jsonMapper) {
        this.rateLimitService = rateLimitService;
        this.maxApiKeyLength = properties.maxApiKeyLength();
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
            writeProblem(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    MISSING_API_KEY_TYPE,
                    "Missing " + API_KEY_HEADER + " header.");
            return false;
        }
        String key = apiKey.strip();
        if (key.length() > maxApiKeyLength) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATE_CHALLENGE);
            writeProblem(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    INVALID_API_KEY_TYPE,
                    "%s header must be at most %d characters.".formatted(API_KEY_HEADER, maxApiKeyLength));
            return false;
        }

        RateLimitDecision decision = rateLimitService.tryAcquire(key);
        response.setHeader(LIMIT_HEADER, Integer.toString(decision.limit()));
        response.setHeader(REMAINING_HEADER, Integer.toString(decision.remaining()));
        if (decision.allowed()) {
            return true;
        }

        long retryAfterSeconds = decision.retryAfterSeconds();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        String detail = "Rate limit of %d requests exceeded; retry in %d %s."
                .formatted(decision.limit(), retryAfterSeconds, retryAfterSeconds == 1 ? "second" : "seconds");
        writeProblem(request, response, HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_TYPE, detail);
        return false;
    }

    private void writeProblem(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, URI type, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
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
