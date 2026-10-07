package com.example.mockretest.booking.dto;

import com.example.mockretest.booking.BookingStatus;
import java.time.Instant;
import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId, Long doctorId, LocalDateTime start, BookingStatus status, Instant expiresAt) {}
