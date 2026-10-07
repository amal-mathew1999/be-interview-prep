package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingSlotPolicyTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 15);

    private final BookingSlotPolicy policy = new BookingSlotPolicy(new BookingProperties(
            LocalTime.of(9, 0), LocalTime.of(17, 0), Duration.ofMinutes(30), Duration.ofMinutes(5), null));

    @Test
    void generatesThirtyMinuteSlotsWithinWorkingHours() {
        List<LocalDateTime> slots = policy.slotsFor(DAY);

        assertThat(slots).hasSize(16);
        assertThat(slots.get(0)).isEqualTo(DAY.atTime(9, 0));
        assertThat(slots.get(1)).isEqualTo(DAY.atTime(9, 30));
        assertThat(slots.get(15)).isEqualTo(DAY.atTime(16, 30));
    }

    @Test
    void respectsConfiguredWorkingHours() {
        BookingSlotPolicy shortDay = new BookingSlotPolicy(new BookingProperties(
                LocalTime.of(10, 0), LocalTime.of(12, 0), Duration.ofMinutes(30), Duration.ofMinutes(5), null));

        assertThat(shortDay.slotsFor(DAY))
                .containsExactly(DAY.atTime(10, 0), DAY.atTime(10, 30), DAY.atTime(11, 0), DAY.atTime(11, 30));
    }

    @Test
    void acceptsAlignedStartsWithinWorkingHours() {
        assertThatCode(() -> policy.validateStart(DAY.atTime(9, 0))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateStart(DAY.atTime(16, 30))).doesNotThrowAnyException();
    }

    @Test
    void rejectsStartNotOnThirtyMinuteBoundary() {
        assertThatThrownBy(() -> policy.validateStart(DAY.atTime(9, 15)))
                .isInstanceOf(BookingInvalidSlotException.class);
        assertThatThrownBy(() -> policy.validateStart(DAY.atTime(9, 0, 1)))
                .isInstanceOf(BookingInvalidSlotException.class);
    }

    @Test
    void rejectsStartOutsideWorkingHours() {
        assertThatThrownBy(() -> policy.validateStart(DAY.atTime(8, 30)))
                .isInstanceOf(BookingInvalidSlotException.class);
        assertThatThrownBy(() -> policy.validateStart(DAY.atTime(17, 0)))
                .isInstanceOf(BookingInvalidSlotException.class);
    }
}
