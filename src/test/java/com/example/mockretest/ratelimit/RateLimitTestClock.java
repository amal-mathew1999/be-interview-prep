package com.example.mockretest.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Controllable clock for rate-limit tests; time only moves when {@link #advance(Duration)} is called. */
public final class RateLimitTestClock extends Clock {

    private final AtomicReference<Instant> now;

    public RateLimitTestClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    public void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("RateLimitTestClock is fixed to UTC");
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
