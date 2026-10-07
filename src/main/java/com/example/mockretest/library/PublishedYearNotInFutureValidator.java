package com.example.mockretest.library;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.Year;
import org.springframework.beans.factory.annotation.Qualifier;

public class PublishedYearNotInFutureValidator implements ConstraintValidator<PublishedYearNotInFuture, Integer> {

    private final Clock clock;

    public PublishedYearNotInFutureValidator(@Qualifier("libraryClock") Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value <= Year.now(clock).getValue();
    }
}
