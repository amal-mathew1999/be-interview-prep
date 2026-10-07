package com.example.mockretest.library;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PublishedYearNotInFutureValidator implements ConstraintValidator<PublishedYearNotInFuture, Integer> {

    private final LibraryClock clock;

    public PublishedYearNotInFutureValidator(LibraryClock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value <= clock.currentYear().getValue();
    }
}
