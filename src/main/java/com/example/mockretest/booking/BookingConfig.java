package com.example.mockretest.booking;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@PropertySource("classpath:booking.properties")
@EnableConfigurationProperties(BookingProperties.class)
public class BookingConfig {

    @Bean
    public BookingClock bookingClock() {
        return new BookingClock(Clock.systemDefaultZone());
    }

    @Bean
    public BookingSlotPolicy bookingSlotPolicy(BookingProperties properties) {
        return new BookingSlotPolicy(properties);
    }
}
