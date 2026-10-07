package com.example.mockretest.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Fixed-window rate limiter keyed by API key.
 *
 * <p>Each key's window is updated with {@link ConcurrentHashMap#compute}, which is atomic per key: concurrent requests
 * for the same key cannot lose updates, while different keys do not contend on a global lock.
 */
@Service
public class RateLimitService {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);

    private final ConcurrentHashMap<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration window;
    private final Clock clock;

    public RateLimitService(RateLimitProperties properties, @Qualifier("rateLimitClock") Clock clock) {
        this.limit = properties.limit();
        this.window = properties.window();
        this.clock = clock;
    }

    /** Records a request for {@code apiKey} and decides whether it is within the limit. */
    public RateLimitDecision tryAcquire(String apiKey) {
        Instant now = clock.instant();
        RateLimitDecision[] decision = new RateLimitDecision[1];
        windows.compute(apiKey, (key, current) -> {
            if (current == null || current.isExpired(now, window)) {
                decision[0] = new RateLimitDecision(true, limit, limit - 1, Duration.ZERO);
                return new RateLimitWindow(now, 1);
            }
            if (current.count() < limit) {
                int count = current.count() + 1;
                decision[0] = new RateLimitDecision(true, limit, limit - count, Duration.ZERO);
                return new RateLimitWindow(current.start(), count);
            }
            Duration retryAfter = Duration.between(now, current.start().plus(window));
            decision[0] = new RateLimitDecision(false, limit, 0, retryAfter);
            return current;
        });
        return decision[0];
    }

    /**
     * Removes windows that have expired so memory does not grow with stale keys. Each removal is atomic per key, so a
     * concurrent request that just opened a fresh window is never discarded.
     *
     * @return number of evicted keys
     */
    @Scheduled(fixedDelayString = "${ratelimit.eviction-interval:PT1M}")
    public int evictExpired() {
        Instant now = clock.instant();
        AtomicInteger evicted = new AtomicInteger();
        for (String key : windows.keySet()) {
            windows.computeIfPresent(key, (k, current) -> {
                if (current.isExpired(now, window)) {
                    evicted.incrementAndGet();
                    return null;
                }
                return current;
            });
        }
        if (evicted.get() > 0) {
            LOG.debug("Evicted {} expired rate-limit windows", evicted.get());
        }
        return evicted.get();
    }

    /** Number of API keys currently tracked. */
    public int trackedKeyCount() {
        return windows.size();
    }

    private record RateLimitWindow(Instant start, int count) {

        boolean isExpired(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}
