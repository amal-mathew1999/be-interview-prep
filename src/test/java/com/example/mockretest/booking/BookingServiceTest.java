package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.mockretest.booking.dto.BookingHoldRequest;
import com.example.mockretest.booking.dto.BookingPatientRequest;
import com.example.mockretest.booking.dto.BookingResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-14T12:00:00Z");
    private static final Duration HOLD = Duration.ofMinutes(5);
    private static final long DOCTOR = 1L;
    private static final LocalDateTime SLOT = LocalDateTime.of(2030, 1, 15, 9, 0);

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingSlotLockRepository slotLockRepository = mock(BookingSlotLockRepository.class);
    private final BookingDoctorRepository doctorRepository = mock(BookingDoctorRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private BookingService service;

    @BeforeEach
    void setUp() {
        BookingProperties properties =
                new BookingProperties(LocalTime.of(9, 0), LocalTime.of(17, 0), Duration.ofMinutes(30), HOLD);
        service = new BookingService(
                bookingRepository,
                slotLockRepository,
                doctorRepository,
                new BookingSlotPolicy(properties),
                events,
                mock(PlatformTransactionManager.class),
                properties,
                new BookingClock(Clock.fixed(NOW, ZoneOffset.UTC)));
        given(doctorRepository.existsById(DOCTOR)).willReturn(true);
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void holdExpiresAtNowPlusHoldTimeout() {
        given(slotLockRepository.findByDoctorIdAndSlotStart(DOCTOR, SLOT)).willReturn(Optional.empty());

        BookingResponse response = service.hold(new BookingHoldRequest(DOCTOR, "p1", SLOT));

        assertThat(response.status()).isEqualTo(BookingStatus.HELD);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(HOLD));
        verify(slotLockRepository).saveAndFlush(any(BookingSlotLock.class));
    }

    @Test
    void holdRejectsUnknownDoctor() {
        assertThatThrownBy(() -> service.hold(new BookingHoldRequest(99L, "p1", SLOT)))
                .isInstanceOf(BookingDoctorNotFoundException.class);
    }

    @Test
    void holdRejectsSlotThatAlreadyStarted() {
        LocalDateTime pastSlot = LocalDateTime.of(2030, 1, 14, 11, 30);

        assertThatThrownBy(() -> service.hold(new BookingHoldRequest(DOCTOR, "p1", pastSlot)))
                .isInstanceOf(BookingSlotInPastException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdRejectsSlotWithUnexpiredHold() {
        given(slotLockRepository.findByDoctorIdAndSlotStart(DOCTOR, SLOT))
                .willReturn(Optional.of(new BookingSlotLock(DOCTOR, SLOT, 5L, NOW.plusMillis(1))));

        assertThatThrownBy(() -> service.hold(new BookingHoldRequest(DOCTOR, "p2", SLOT)))
                .isInstanceOf(BookingSlotUnavailableException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdReclaimsHoldExpiringExactlyNow() {
        BookingSlotLock expired = new BookingSlotLock(DOCTOR, SLOT, 5L, NOW);
        Booking previous = Booking.hold(DOCTOR, "p1", SLOT, NOW);
        given(slotLockRepository.findByDoctorIdAndSlotStart(DOCTOR, SLOT)).willReturn(Optional.of(expired));
        given(bookingRepository.findById(5L)).willReturn(Optional.of(previous));

        BookingResponse response = service.hold(new BookingHoldRequest(DOCTOR, "p2", SLOT));

        assertThat(response.status()).isEqualTo(BookingStatus.HELD);
        assertThat(previous.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(slotLockRepository).delete(expired);
    }

    @Test
    void confirmJustBeforeExpiryConfirmsAndPublishesEvent() {
        Booking booking = heldBooking(7L, NOW.plusMillis(1));
        BookingSlotLock lock = new BookingSlotLock(DOCTOR, SLOT, 7L, NOW.plusMillis(1));
        given(slotLockRepository.findByBookingId(7L)).willReturn(Optional.of(lock));

        BookingResponse response = service.confirm(7L, new BookingPatientRequest("p1"));

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.expiresAt()).isNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(lock.isExpired(NOW.plus(Duration.ofDays(1)))).isFalse();
        verify(events).publishEvent(any(BookingConfirmedEvent.class));
    }

    @Test
    void confirmAtExactExpiryIsRejectedAndNotPublished() {
        Booking booking = heldBooking(7L, NOW);

        assertThatThrownBy(() -> service.confirm(7L, new BookingPatientRequest("p1")))
                .isInstanceOf(BookingHoldExpiredException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void confirmByOtherPatientIsForbidden() {
        heldBooking(7L, NOW.plus(HOLD));

        assertThatThrownBy(() -> service.confirm(7L, new BookingPatientRequest("intruder")))
                .isInstanceOf(BookingForbiddenException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void confirmUnknownBookingIsNotFound() {
        given(bookingRepository.findById(8L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(8L, new BookingPatientRequest("p1")))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancelOfExpiredHoldIsConflict() {
        heldBooking(7L, NOW);

        assertThatThrownBy(() -> service.cancel(7L, new BookingPatientRequest("p1")))
                .isInstanceOf(BookingStateConflictException.class);
    }

    @Test
    void cancelOfConfirmedBookingReleasesSlot() {
        Booking booking = heldBooking(7L, NOW.plus(HOLD));
        booking.confirm();
        BookingSlotLock lock = new BookingSlotLock(DOCTOR, SLOT, 7L, null);
        given(slotLockRepository.findByBookingId(7L)).willReturn(Optional.of(lock));

        BookingResponse response = service.cancel(7L, new BookingPatientRequest("p1"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(slotLockRepository).delete(lock);
    }

    private Booking heldBooking(long id, Instant expiresAt) {
        Booking booking = Booking.hold(DOCTOR, "p1", SLOT, expiresAt);
        given(bookingRepository.findById(id)).willReturn(Optional.of(booking));
        return booking;
    }
}
