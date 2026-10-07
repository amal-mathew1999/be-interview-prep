package com.example.mockretest.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank
                @Pattern(
                        regexp = "(?i)\\s*(FOOD|TRAVEL|BILLS|OTHER)\\s*",
                        message = "must be one of FOOD, TRAVEL, BILLS, OTHER")
                String category,
        @NotNull LocalDate date,
        @Size(max = 500) String note) {}
