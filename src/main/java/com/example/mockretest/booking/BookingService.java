package com.example.mockretest.booking;

import com.example.mockretest.booking.dto.BookingHoldRequest;
import com.example.mockretest.booking.dto.BookingPatientRequest;
import com.example.mockretest.booking.dto.BookingResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSlotLockRepository slotLockRepository;
    private final BookingDoctorRepository doctorRepository;
    private final BookingSlotPolicy slotPolicy;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final BookingClock clock;
    private final Duration holdTimeout;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSlotLockRepository slotLockRepository,
            BookingDoctorRepository doctorRepository,
            BookingSlotPolicy slotPolicy,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            BookingProperties properties,
            BookingClock clock) {
        this.bookingRepository = bookingRepository;
        this.slotLockRepository = slotLockRepository;
        this.doctorRepository = doctorRepository;
        this.slotPolicy = slotPolicy;
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.holdTimeout = properties.holdTimeout();
    }

    /**
     * Places a hold on a slot. The unique slot-lock row decides races in the database: losers of a concurrent insert
     * (or of a concurrent reclaim of an expired hold) get {@link BookingSlotUnavailableException}.
     */
    public BookingResponse hold(BookingHoldRequest request) {
        long doctorId = request.doctorId();
        LocalDateTime start = request.start();
        try {
            return transactionTemplate.execute(tx -> holdInTransaction(doctorId, request.patientId(), start));
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            throw new BookingSlotUnavailableException(doctorId, start);
        }
    }

    private BookingResponse holdInTransaction(long doctorId, String patientId, LocalDateTime start) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new BookingDoctorNotFoundException(doctorId);
        }
        slotPolicy.validateStart(start);
        if (start.isBefore(clock.localNow())) {
            throw new BookingSlotInPastException(start);
        }
        Instant now = clock.instant();

        slotLockRepository.findByDoctorIdAndSlotStart(doctorId, start).ifPresent(existing -> {
            if (!existing.isExpired(now)) {
                throw new BookingSlotUnavailableException(doctorId, start);
            }
            reclaimExpiredHold(existing);
        });

        Booking booking = bookingRepository.save(Booking.hold(doctorId, patientId, start, now.plus(holdTimeout)));
        slotLockRepository.saveAndFlush(
                new BookingSlotLock(doctorId, start, booking.getId(), booking.getHoldExpiresAt()));
        return toResponse(booking, now);
    }

    private void reclaimExpiredHold(BookingSlotLock expired) {
        bookingRepository.findById(expired.getBookingId()).ifPresent(Booking::expire);
        slotLockRepository.delete(expired);
        slotLockRepository.flush();
    }

    @Transactional
    public BookingResponse confirm(long bookingId, BookingPatientRequest request) {
        Booking booking = loadOwned(bookingId, request.patientId());
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new BookingStateConflictException(bookingId, booking.getStatus(), "confirm");
        }
        if (booking.isHoldExpired(now)) {
            throw new BookingHoldExpiredException(bookingId);
        }
        BookingSlotLock lock = slotLockRepository
                .findByBookingId(bookingId)
                .orElseThrow(() -> new BookingHoldExpiredException(bookingId));
        booking.confirm();
        lock.markConfirmed();
        events.publishEvent(new BookingConfirmedEvent(
                booking.getId(), booking.getDoctorId(), booking.getPatientId(), booking.getSlotStart()));
        return toResponse(booking, now);
    }

    @Transactional
    public BookingResponse cancel(long bookingId, BookingPatientRequest request) {
        Booking booking = loadOwned(bookingId, request.patientId());
        Instant now = clock.instant();
        BookingStatus current = booking.effectiveStatus(now);
        if (current != BookingStatus.CONFIRMED && current != BookingStatus.HELD) {
            throw new BookingStateConflictException(bookingId, current, "cancel");
        }
        slotLockRepository.findByBookingId(bookingId).ifPresent(slotLockRepository::delete);
        booking.cancel();
        return toResponse(booking, now);
    }

    private Booking loadOwned(long bookingId, String patientId) {
        Booking booking =
                bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.isOwnedBy(patientId)) {
            throw new BookingForbiddenException(bookingId);
        }
        return booking;
    }

    private static BookingResponse toResponse(Booking booking, Instant now) {
        BookingStatus status = booking.effectiveStatus(now);
        return new BookingResponse(
                booking.getId(),
                booking.getDoctorId(),
                booking.getSlotStart(),
                status,
                status == BookingStatus.HELD || status == BookingStatus.EXPIRED ? booking.getHoldExpiresAt() : null);
    }
}
