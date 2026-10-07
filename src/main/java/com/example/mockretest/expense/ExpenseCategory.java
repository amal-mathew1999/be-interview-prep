package com.example.mockretest.expense;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ExpenseCategory {
    FOOD,
    TRAVEL,
    BILLS,
    OTHER;

    /** Case-insensitive lookup; empty when the value is not a known category. */
    public static Optional<ExpenseCategory> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.name().equals(normalized)).findFirst();
    }
}
