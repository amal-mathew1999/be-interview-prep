package com.example.mockretest.booking;

import org.springframework.http.HttpStatus;

public class BookingStateConflictException extends BookingProblemException {

    public BookingStateConflictException(long bookingId, BookingStatus current, String action) {
        super(
                HttpStatus.CONFLICT,
                "invalid-booking-state",
                "Invalid booking state",
                "Cannot " + action + " booking " + bookingId + " in status " + current);
    }
}
