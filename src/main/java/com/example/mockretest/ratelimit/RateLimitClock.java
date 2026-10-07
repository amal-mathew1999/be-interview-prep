package com.example.mockretest.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Feature-specific time source for rate-limit windows.
 *
 * <p>Wrapping the {@link Clock} in a dedicated type keeps this feature from publishing a raw {@code Clock} bean, which
 * would make by-type {@code Clock} injection in other features ambiguous.
 *
 * @param clock underlying clock
 */
public record RateLimitClock(Clock clock) {

    public RateLimitClock {
        Objects.requireNonNull(clock, "clock");
    }

    /** Current instant according to the underlying clock. */
    public Instant now() {
        return clock.instant();
    }
}
