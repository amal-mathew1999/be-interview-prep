package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class BookingNotifierTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(BookingNotifier.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void confirmationLogIdentifiesBookingWithoutPatientId() {
        new BookingNotifier()
                .sendConfirmation(
                        new BookingConfirmedEvent(42L, 7L, "patient-secret-123", LocalDateTime.of(2030, 1, 15, 9, 0)));

        assertThat(appender.list).isNotEmpty().allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain("patient-secret-123"));
        assertThat(appender.list)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("42"));
    }
}
