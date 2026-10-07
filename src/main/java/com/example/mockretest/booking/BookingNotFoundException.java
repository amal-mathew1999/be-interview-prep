package com.example.mockretest.booking;

import org.springframework.http.HttpStatus;

public class BookingNotFoundException extends BookingProblemException {

    public BookingNotFoundException(long bookingId) {
        super(
                HttpStatus.NOT_FOUND,
                "booking-not-found",
                "Booking not found",
                "Booking " + bookingId + " does not exist");
    }
}
