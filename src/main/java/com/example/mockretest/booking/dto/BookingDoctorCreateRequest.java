package com.example.mockretest.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingDoctorCreateRequest(@NotBlank @Size(max = 200) String name) {}
