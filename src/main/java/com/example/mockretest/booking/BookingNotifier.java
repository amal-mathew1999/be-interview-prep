package com.example.mockretest.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends booking notifications. Logging stands in for a real email/SMS channel. */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);

    public void sendConfirmation(BookingConfirmedEvent event) {
        log.info(
                "Booking {} confirmed: patient {} with doctor {} at {}",
                event.bookingId(),
                event.patientId(),
                event.doctorId(),
                event.start());
    }
}
