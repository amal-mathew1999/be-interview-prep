package com.example.mockretest.booking;

import org.springframework.http.HttpStatus;

public class BookingHoldExpiredException extends BookingProblemException {

    public BookingHoldExpiredException(long bookingId) {
        super(HttpStatus.CONFLICT, "hold-expired", "Hold expired", "Hold for booking " + bookingId + " has expired");
    }
}
