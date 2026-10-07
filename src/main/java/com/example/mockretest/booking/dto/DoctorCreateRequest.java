package com.example.mockretest.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DoctorCreateRequest(@NotBlank @Size(max = 200) String name) {}
