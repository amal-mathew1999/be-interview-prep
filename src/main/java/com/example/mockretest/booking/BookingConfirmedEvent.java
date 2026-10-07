package com.example.mockretest.booking;

import java.time.LocalDateTime;

/** Published inside the confirming transaction; delivered to listeners only once it commits. */
public record BookingConfirmedEvent(Long bookingId, Long doctorId, String patientId, LocalDateTime start) {}
