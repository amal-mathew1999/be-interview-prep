package com.example.mockretest.library;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.Objects;

/**
 * The library feature's time source. Wrapping {@link Clock} in a feature type keeps the merged application free of a
 * raw {@code java.time.Clock} bean, so unqualified {@code Clock} injection elsewhere can never become ambiguous.
 */
public record LibraryClock(Clock clock) {

    public LibraryClock {
        Objects.requireNonNull(clock, "clock");
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public Year currentYear() {
        return Year.now(clock);
    }
}
