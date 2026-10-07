package com.example.mockretest.booking;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

public class BookingSlotUnavailableException extends BookingProblemException {

    public BookingSlotUnavailableException(long doctorId, LocalDateTime start) {
        super(
                HttpStatus.CONFLICT,
                "slot-unavailable",
                "Slot unavailable",
                "Slot " + start + " of doctor " + doctorId + " is already held or booked");
    }
}
