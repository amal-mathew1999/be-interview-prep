package com.example.mockretest.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record BookingPatientRequest(@NotBlank String patientId) {}
