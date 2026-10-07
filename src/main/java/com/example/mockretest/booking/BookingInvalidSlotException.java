package com.example.mockretest.booking;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;

public class BookingInvalidSlotException extends BookingProblemException {

    public BookingInvalidSlotException(
            LocalDateTime start, LocalTime workdayStart, LocalTime workdayEnd, Duration slotDuration) {
        super(
                HttpStatus.BAD_REQUEST,
                "invalid-slot",
                "Invalid slot",
                "Start " + start + " is not a " + slotDuration.toMinutes() + "-minute slot boundary between "
                        + workdayStart + " and " + workdayEnd);
    }
}
