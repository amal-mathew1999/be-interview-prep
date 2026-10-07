package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mockretest.booking.dto.BookingDoctorCreateRequest;
import com.example.mockretest.booking.dto.BookingHoldRequest;
import com.example.mockretest.booking.dto.BookingResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class BookingConcurrencyTest {

    private static final int PATIENTS = 20;

    /** Test time is pinned to this instant, so slot dates below stay in the future whatever today's date is. */
    private static final Instant NOW = Instant.parse("2030-01-14T12:00:00Z");

    private static final LocalDate SLOT_DAY = LocalDate.of(2030, 2, 1);

    @TestBean(name = "bookingClock", methodName = "controllableClock")
    private BookingClock bookingClock;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingDoctorService doctorService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSlotLockRepository slotLockRepository;

    @Autowired
    private BookingProperties properties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    static BookingClock controllableClock() {
        return new BookingClock(new BookingTestClock(NOW));
    }

    @BeforeEach
    void resetClock() {
        clock().set(NOW);
    }

    @Test
    void exactlyOneOfTwentyConcurrentHoldsOnSameSlotSucceeds() throws Exception {
        long doctorId = createDoctor("Dr. Concurrent");
        LocalDateTime slot = SLOT_DAY.atTime(10, 0);

        RaceOutcome outcome = raceHolds(doctorId, slot);

        assertThat(outcome.winners()).hasSize(1);
        assertThat(outcome.conflicts()).isEqualTo(PATIENTS - 1);
        assertThat(locksOn(doctorId, slot)).singleElement().satisfies(lock -> assertThat(lock.getBookingId())
                .isEqualTo(outcome.winners().get(0).bookingId()));
    }

    @Test
    void exactlyOneOfTwentyConcurrentReclaimsOfAnExpiredHoldSucceeds() throws Exception {
        long doctorId = createDoctor("Dr. Reclaim");
        LocalDateTime slot = SLOT_DAY.atTime(11, 0);
        long expiredBookingId = bookingService
                .hold(new BookingHoldRequest(doctorId, "first-patient", slot))
                .bookingId();
        clock().advance(properties.holdTimeout().plusSeconds(1));

        RaceOutcome outcome = raceHolds(doctorId, slot);

        assertThat(outcome.winners()).hasSize(1);
        assertThat(outcome.conflicts()).isEqualTo(PATIENTS - 1);
        long winnerId = outcome.winners().get(0).bookingId();
        assertThat(locksOn(doctorId, slot)).singleElement().satisfies(lock -> assertThat(lock.getBookingId())
                .isEqualTo(winnerId));
        assertThat(bookingRepository.findById(expiredBookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingRepository.findAll().stream()
                        .filter(booking -> booking.getDoctorId() == doctorId)
                        .filter(booking -> booking.getStatus() == BookingStatus.HELD))
                .singleElement()
                .satisfies(booking -> assertThat(booking.getId()).isEqualTo(winnerId));
    }

    /**
     * A reclaim that read a slot lock which another transaction has since changed (e.g. confirmed) must not delete
     * it: the lock's optimistic version makes the stale delete fail instead of silently freeing a booked slot.
     */
    @Test
    void staleSlotLockDeleteIsRejectedAfterConcurrentChange() {
        long doctorId = createDoctor("Dr. Stale");
        LocalDateTime slot = SLOT_DAY.atTime(12, 0);
        bookingService.hold(new BookingHoldRequest(doctorId, "patient", slot));
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(tx -> {
                    BookingSlotLock staleView = slotLockRepository
                            .findByDoctorIdAndSlotStart(doctorId, slot)
                            .orElseThrow();
                    concurrent.executeWithoutResult(other -> slotLockRepository
                            .findByDoctorIdAndSlotStart(doctorId, slot)
                            .orElseThrow()
                            .markConfirmed());
                    slotLockRepository.delete(staleView);
                    slotLockRepository.flush();
                }))
                .isInstanceOf(ConcurrencyFailureException.class);

        assertThat(locksOn(doctorId, slot)).hasSize(1);
    }

    private RaceOutcome raceHolds(long doctorId, LocalDateTime slot) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(PATIENTS);
        List<Future<BookingResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < PATIENTS; i++) {
                String patientId = "patient-" + i;
                Callable<BookingResponse> task = () -> {
                    startGate.await();
                    return bookingService.hold(new BookingHoldRequest(doctorId, patientId, slot));
                };
                results.add(pool.submit(task));
            }
            startGate.countDown();

            List<BookingResponse> winners = new ArrayList<>();
            int conflicts = 0;
            for (Future<BookingResponse> result : results) {
                try {
                    BookingResponse response = result.get(60, TimeUnit.SECONDS);
                    assertThat(response.status()).isEqualTo(BookingStatus.HELD);
                    winners.add(response);
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BookingSlotUnavailableException.class);
                    assertThat(((BookingProblemException) e.getCause()).getStatus())
                            .isEqualTo(HttpStatus.CONFLICT);
                    conflicts++;
                }
            }
            return new RaceOutcome(winners, conflicts);
        } finally {
            pool.shutdownNow();
        }
    }

    private List<BookingSlotLock> locksOn(long doctorId, LocalDateTime slot) {
        return slotLockRepository.findByDoctorIdAndSlotStartBetween(doctorId, slot, slot);
    }

    private long createDoctor(String name) {
        return doctorService.create(new BookingDoctorCreateRequest(name)).id();
    }

    private BookingTestClock clock() {
        return (BookingTestClock) bookingClock.delegate();
    }

    private record RaceOutcome(List<BookingResponse> winners, int conflicts) {}
}
