package com.example.mockretest.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Controllable clock for tests: time only moves when {@link #advance(Duration)} is called. */
final class BookingTestClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    BookingTestClock(Instant start) {
        this(new AtomicReference<>(start), ZoneOffset.UTC);
    }

    private BookingTestClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new BookingTestClock(now, newZone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
