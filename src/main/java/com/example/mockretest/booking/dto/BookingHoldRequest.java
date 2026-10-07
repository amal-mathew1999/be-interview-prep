package com.example.mockretest.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record BookingHoldRequest(
        @NotNull Long doctorId, @NotBlank @Size(max = 100) String patientId, @NotNull LocalDateTime start) {}
