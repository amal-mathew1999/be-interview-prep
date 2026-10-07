package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {

    @Test
    void retryAfterRoundsUpAnyNanosecondRemainder() {
        assertThat(rejectedAfter(Duration.ofSeconds(40).plusNanos(1)).retryAfterSeconds())
                .isEqualTo(41);
        assertThat(rejectedAfter(Duration.ofSeconds(40).plusNanos(500_000)).retryAfterSeconds())
                .isEqualTo(41);
    }

    @Test
    void retryAfterKeepsExactWholeSeconds() {
        assertThat(rejectedAfter(Duration.ofSeconds(40)).retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        assertThat(rejectedAfter(Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
        assertThat(rejectedAfter(Duration.ofNanos(1)).retryAfterSeconds()).isEqualTo(1);
    }

    private static RateLimitDecision rejectedAfter(Duration retryAfter) {
        return new RateLimitDecision(false, 10, 0, retryAfter);
    }
}
