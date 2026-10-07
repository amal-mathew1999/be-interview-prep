package com.example.mockretest.booking;

import java.time.Duration;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Booking configuration bound from {@code booking.properties}. */
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        LocalTime workdayStart, LocalTime workdayEnd, Duration slotDuration, Duration holdTimeout) {

    public BookingProperties {
        if (workdayStart == null || workdayEnd == null || slotDuration == null || holdTimeout == null) {
            throw new IllegalArgumentException("booking.* properties must all be set");
        }
        if (!workdayStart.isBefore(workdayEnd)) {
            throw new IllegalArgumentException("booking.workday-start must be before booking.workday-end");
        }
        if (slotDuration.isZero() || slotDuration.isNegative() || holdTimeout.isZero() || holdTimeout.isNegative()) {
            throw new IllegalArgumentException("booking.slot-duration and booking.hold-timeout must be positive");
        }
    }
}
