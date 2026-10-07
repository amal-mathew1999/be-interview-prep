package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private RateLimitTestClock clock;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        clock = new RateLimitTestClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = serviceWithMaxTrackedKeys(100_000);
    }

    @Test
    void allowsUpToLimitAndReportsRemaining() {
        for (int i = 1; i <= LIMIT; i++) {
            RateLimitDecision decision = service.tryAcquire("key-a");
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(LIMIT);
            assertThat(decision.remaining()).isEqualTo(LIMIT - i);
        }
    }

    @Test
    void rejectsEleventhRequestWithRetryAfter() {
        exhaust("key-a");
        clock.advance(Duration.ofSeconds(20));

        RateLimitDecision decision = service.tryAcquire("key-a");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        exhaust("key-a");
        clock.advance(Duration.ofMillis(18_500));

        assertThat(service.tryAcquire("key-a").retryAfterSeconds()).isEqualTo(42);
    }

    @Test
    void retryAfterRoundsUpSubMillisecondRemainder() {
        exhaust("key-a");
        clock.advance(Duration.ofSeconds(20).minusNanos(500));

        assertThat(service.tryAcquire("key-a").retryAfterSeconds()).isEqualTo(41);
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        exhaust("key-a");
        clock.advance(Duration.ofMillis(59_999));

        RateLimitDecision decision = service.tryAcquire("key-a");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void differentKeysAreIndependent() {
        exhaust("key-a");

        assertThat(service.tryAcquire("key-a").allowed()).isFalse();
        RateLimitDecision other = service.tryAcquire("key-b");
        assertThat(other.allowed()).isTrue();
        assertThat(other.remaining()).isEqualTo(LIMIT - 1);
    }

    @Test
    void allowsRequestsAgainAfterWindowPasses() {
        exhaust("key-a");
        assertThat(service.tryAcquire("key-a").allowed()).isFalse();

        clock.advance(WINDOW);

        RateLimitDecision decision = service.tryAcquire("key-a");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(LIMIT - 1);
    }

    @Test
    void stillRejectsJustBeforeWindowEnds() {
        exhaust("key-a");
        clock.advance(WINDOW.minusMillis(1));

        assertThat(service.tryAcquire("key-a").allowed()).isFalse();
    }

    @Test
    void concurrentBurstForOneKeyYieldsExactlyLimitSuccesses() throws Exception {
        int threads = 64;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> task = () -> {
                    ready.countDown();
                    start.await();
                    return service.tryAcquire("burst-key").allowed();
                };
                results.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(countSuccesses(results)).isEqualTo(LIMIT);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentBurstsForDifferentKeysEachYieldLimitSuccesses() throws Exception {
        int keys = 8;
        int perKey = 30;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<List<Future<Boolean>>> results = new ArrayList<>();
            for (int k = 0; k < keys; k++) {
                String key = "key-" + k;
                List<Future<Boolean>> keyResults = new ArrayList<>();
                for (int i = 0; i < perKey; i++) {
                    Callable<Boolean> task = () -> {
                        start.await();
                        return service.tryAcquire(key).allowed();
                    };
                    keyResults.add(executor.submit(task));
                }
                results.add(keyResults);
            }
            start.countDown();

            for (List<Future<Boolean>> keyResults : results) {
                assertThat(countSuccesses(keyResults)).isEqualTo(LIMIT);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void evictsOnlyExpiredWindows() {
        service.tryAcquire("stale");
        clock.advance(Duration.ofSeconds(30));
        service.tryAcquire("fresh");
        clock.advance(Duration.ofSeconds(30));

        int evicted = service.evictExpired();

        assertThat(evicted).isEqualTo(1);
        assertThat(service.trackedKeyCount()).isEqualTo(1);
        assertThat(service.tryAcquire("fresh").remaining()).isEqualTo(LIMIT - 2);
    }

    @Test
    void evictedKeyStartsFreshWindow() {
        exhaust("key-a");
        clock.advance(WINDOW);
        service.evictExpired();

        assertThat(service.trackedKeyCount()).isZero();
        assertThat(service.tryAcquire("key-a").remaining()).isEqualTo(LIMIT - 1);
    }

    @Test
    void rejectsNewKeyWhenTrackedKeyCapIsReachedAndNothingHasExpired() {
        RateLimitService capped = serviceWithMaxTrackedKeys(2);
        capped.tryAcquire("key-a");
        capped.tryAcquire("key-b");

        RateLimitDecision decision = capped.tryAcquire("key-c");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.capacityExceeded()).isTrue();
        assertThat(decision.retryAfterSeconds()).isEqualTo(1);
        assertThat(capped.trackedKeyCount()).isEqualTo(2);
    }

    @Test
    void alreadyTrackedKeysKeepWorkingAtTrackedKeyCap() {
        RateLimitService capped = serviceWithMaxTrackedKeys(2);
        capped.tryAcquire("key-a");
        capped.tryAcquire("key-b");

        RateLimitDecision decision = capped.tryAcquire("key-a");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.capacityExceeded()).isFalse();
        assertThat(decision.remaining()).isEqualTo(LIMIT - 2);
    }

    @Test
    void evictsExpiredWindowsInlineToAdmitNewKeyAtTrackedKeyCap() {
        RateLimitService capped = serviceWithMaxTrackedKeys(2);
        capped.tryAcquire("key-a");
        capped.tryAcquire("key-b");
        clock.advance(WINDOW);

        RateLimitDecision decision = capped.tryAcquire("key-c");

        assertThat(decision.allowed()).isTrue();
        assertThat(capped.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void manyFreshKeysNeverGrowTrackedKeysBeyondCap() {
        RateLimitService capped = serviceWithMaxTrackedKeys(5);

        for (int i = 0; i < 1_000; i++) {
            capped.tryAcquire("fresh-" + i);
        }

        assertThat(capped.trackedKeyCount()).isEqualTo(5);
    }

    private RateLimitService serviceWithMaxTrackedKeys(int maxTrackedKeys) {
        return new RateLimitService(
                new RateLimitProperties(LIMIT, WINDOW, Duration.ofMinutes(1), 128, maxTrackedKeys),
                new RateLimitClock(clock));
    }

    private void exhaust(String key) {
        for (int i = 0; i < LIMIT; i++) {
            assertThat(service.tryAcquire(key).allowed()).isTrue();
        }
    }

    private static int countSuccesses(List<Future<Boolean>> results) throws Exception {
        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get(10, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        return successes;
    }
}
