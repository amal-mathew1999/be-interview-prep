package com.example.mockretest.booking;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

public class BookingSlotInPastException extends BookingProblemException {

    public BookingSlotInPastException(LocalDateTime start) {
        super(HttpStatus.BAD_REQUEST, "slot-in-past", "Slot in the past", "Slot " + start + " has already started");
    }
}
