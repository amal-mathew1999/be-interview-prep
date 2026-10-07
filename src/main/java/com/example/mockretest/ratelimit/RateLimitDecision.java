package com.example.mockretest.ratelimit;

import java.time.Duration;

/**
 * Outcome of a rate-limit check for one request.
 *
 * @param allowed whether the request may proceed
 * @param limit configured maximum requests per window
 * @param remaining requests still allowed in the current window
 * @param retryAfter time until the client may retry ({@link Duration#ZERO} when allowed)
 * @param capacityExceeded whether the request was rejected because the limiter already tracks the maximum number of
 *     keys, rather than because this key exhausted its own limit
 */
public record RateLimitDecision(
        boolean allowed, int limit, int remaining, Duration retryAfter, boolean capacityExceeded) {

    /** A decision about this key's own limit (allowed, or rejected because the key exhausted its window). */
    public RateLimitDecision(boolean allowed, int limit, int remaining, Duration retryAfter) {
        this(allowed, limit, remaining, retryAfter, false);
    }

    /** Rejection of a not-yet-tracked key because the limiter is at {@code ratelimit.max-tracked-keys}. */
    static RateLimitDecision capacityExceeded(int limit, Duration retryAfter) {
        return new RateLimitDecision(false, limit, 0, retryAfter, true);
    }

    /** Whole seconds until the client may retry, rounded up and never less than one. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.getSeconds();
        if (retryAfter.getNano() > 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }
}
