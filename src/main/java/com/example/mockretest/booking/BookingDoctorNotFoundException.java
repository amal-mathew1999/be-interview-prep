package com.example.mockretest.booking;

import org.springframework.http.HttpStatus;

public class BookingDoctorNotFoundException extends BookingProblemException {

    public BookingDoctorNotFoundException(long doctorId) {
        super(HttpStatus.NOT_FOUND, "doctor-not-found", "Doctor not found", "Doctor " + doctorId + " does not exist");
    }
}
