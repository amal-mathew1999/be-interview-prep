package com.example.mockretest.library;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LibraryPublishedYearNotInFutureValidator
        implements ConstraintValidator<LibraryPublishedYearNotInFuture, Integer> {

    private final LibraryClock clock;

    public LibraryPublishedYearNotInFutureValidator(LibraryClock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value <= clock.currentYear().getValue();
    }
}
