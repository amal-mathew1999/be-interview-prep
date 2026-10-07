package com.example.mockretest.booking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Pure rules for the slot grid of a working day. */
public class BookingSlotPolicy {

    private final LocalTime workdayStart;
    private final LocalTime workdayEnd;
    private final Duration slotDuration;

    public BookingSlotPolicy(BookingProperties properties) {
        this.workdayStart = properties.workdayStart();
        this.workdayEnd = properties.workdayEnd();
        this.slotDuration = properties.slotDuration();
    }

    public List<LocalDateTime> slotsFor(LocalDate date) {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime end = date.atTime(workdayEnd);
        for (LocalDateTime slot = date.atTime(workdayStart);
                !slot.plus(slotDuration).isAfter(end);
                slot = slot.plus(slotDuration)) {
            slots.add(slot);
        }
        return slots;
    }

    public void validateStart(LocalDateTime start) {
        if (!slotsFor(start.toLocalDate()).contains(start)) {
            throw new BookingInvalidSlotException(start, workdayStart, workdayEnd, slotDuration);
        }
    }
}
