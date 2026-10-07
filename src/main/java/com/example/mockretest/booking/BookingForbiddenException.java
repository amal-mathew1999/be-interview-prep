package com.example.mockretest.booking;

import org.springframework.http.HttpStatus;

public class BookingForbiddenException extends BookingProblemException {

    public BookingForbiddenException(long bookingId) {
        super(
                HttpStatus.FORBIDDEN,
                "not-booking-owner",
                "Not the booking owner",
                "Booking " + bookingId + " belongs to another patient");
    }
}
