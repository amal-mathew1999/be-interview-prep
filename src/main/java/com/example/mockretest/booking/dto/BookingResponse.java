package com.example.mockretest.booking.dto;

import com.example.mockretest.booking.BookingStatus;
import java.time.Instant;
import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        Long doctorId,
        String patientId,
        LocalDateTime start,
        BookingStatus status,
        Instant expiresAt) {}
