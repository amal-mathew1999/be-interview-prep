package com.example.mockretest.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fixed-window rate limiter keyed by API key.
 *
 * <p>Each key's window is updated with {@link ConcurrentHashMap#compute}, which is atomic per key: concurrent requests
 * for the same key cannot lose updates, while different keys do not contend on a global lock.
 *
 * <p>At most {@code ratelimit.max-tracked-keys} keys are tracked between scheduled sweeps. When a new key arrives at
 * that cap, expired windows are evicted inline (at most once per {@link #INLINE_SWEEP_GAP}, so a full map of live keys
 * cannot turn every request into a full scan); if the map is still full, the new key is rejected. The cap is soft
 * under concurrency: it may be exceeded by at most the number of threads admitting new keys simultaneously.
 */
@Service
public class RateLimitService {

    /** Minimum time between inline sweeps triggered by the tracked-key cap; also the retry hint when it is hit. */
    static final Duration INLINE_SWEEP_GAP = Duration.ofSeconds(1);

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);

    private final ConcurrentHashMap<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastInlineSweep = new AtomicReference<>(Instant.MIN);
    private final int limit;
    private final Duration window;
    private final int maxTrackedKeys;
    private final RateLimitClock clock;

    public RateLimitService(RateLimitProperties properties, RateLimitClock clock) {
        this.limit = properties.limit();
        this.window = properties.window();
        this.maxTrackedKeys = properties.maxTrackedKeys();
        this.clock = clock;
    }

    /** Records a request for {@code apiKey} and decides whether it is within the limit. */
    public RateLimitDecision tryAcquire(String apiKey) {
        Instant now = clock.now();
        if (!windows.containsKey(apiKey) && atCapacity(now)) {
            LOG.debug("Rejecting new API key: {} keys already tracked", windows.size());
            return RateLimitDecision.capacityExceeded(limit, INLINE_SWEEP_GAP);
        }
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
     * concurrent request that just opened a fresh window is never discarded. Scheduled by {@link RateLimitConfig} at
     * {@code ratelimit.eviction-interval}.
     *
     * @return number of evicted keys
     */
    public int evictExpired() {
        return evictExpired(clock.now());
    }

    private int evictExpired(Instant now) {
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

    /** Whether the cap is reached even after an (at most once per {@link #INLINE_SWEEP_GAP}) inline sweep. */
    private boolean atCapacity(Instant now) {
        if (windows.size() < maxTrackedKeys) {
            return false;
        }
        Instant last = lastInlineSweep.get();
        if (!now.isBefore(last.plus(INLINE_SWEEP_GAP)) && lastInlineSweep.compareAndSet(last, now)) {
            evictExpired(now);
        }
        return windows.size() >= maxTrackedKeys;
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
