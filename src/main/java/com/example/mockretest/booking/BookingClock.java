package com.example.mockretest.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Feature-specific time source for booking. Wrapping {@link Clock} in a booking-owned type keeps this feature from
 * contributing a raw {@code java.time.Clock} bean that could make other features' unqualified {@code Clock}
 * injection ambiguous.
 */
public final class BookingClock {

    private final Clock clock;

    public BookingClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Instant instant() {
        return clock.instant();
    }

    /** Underlying clock; package-private so tests can drive a controllable clock. */
    Clock delegate() {
        return clock;
    }

    /** Wall-clock date-time in the clock's zone, comparable with slot start times. */
    public LocalDateTime localNow() {
        return LocalDateTime.now(clock);
    }
}
