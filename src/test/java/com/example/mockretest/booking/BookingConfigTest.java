package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BookingConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(BookingConfig.class);

    @Test
    void bookingClockDefaultsToUtcRegardlessOfJvmDefaultZone() {
        TimeZone jvmDefault = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
        try {
            contextRunner.run(context -> {
                ZoneId utc = ZoneId.of("UTC");
                assertThat(context.getBean(BookingProperties.class).zone()).isEqualTo(utc);
                assertThat(context.getBean(BookingClock.class).delegate().getZone())
                        .isEqualTo(utc);
            });
        } finally {
            TimeZone.setDefault(jvmDefault);
        }
    }

    @Test
    void zoneDefaultsToUtcWhenNotConfigured() {
        BookingProperties properties = new BookingProperties(
                LocalTime.of(9, 0), LocalTime.of(17, 0), Duration.ofMinutes(30), Duration.ofMinutes(5), null);

        assertThat(properties.zone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void bookingClockUsesConfiguredClinicZone() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        contextRunner.withPropertyValues("booking.zone=Asia/Kolkata").run(context -> {
            BookingClock clock = context.getBean(BookingClock.class);

            assertThat(clock.delegate().getZone()).isEqualTo(kolkata);
            LocalDateTime clinicNow = LocalDateTime.ofInstant(clock.instant(), kolkata);
            assertThat(clock.localNow()).isBetween(clinicNow.minusMinutes(1), clinicNow.plusMinutes(1));
        });
    }
}
