package com.example.mockretest.ratelimit;

import java.time.Duration;

/**
 * Outcome of a rate-limit check for one request.
 *
 * @param allowed whether the request may proceed
 * @param limit configured maximum requests per window
 * @param remaining requests still allowed in the current window
 * @param retryAfter time until the current window ends ({@link Duration#ZERO} when allowed)
 */
public record RateLimitDecision(boolean allowed, int limit, int remaining, Duration retryAfter) {

    /** Whole seconds until the client may retry, rounded up and never less than one. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.getSeconds();
        if (retryAfter.getNano() > 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }
}
