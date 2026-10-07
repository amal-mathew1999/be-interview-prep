package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mockretest.booking.dto.BookingHoldRequest;
import com.example.mockretest.booking.dto.BookingResponse;
import com.example.mockretest.booking.dto.DoctorCreateRequest;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

@SpringBootTest
class BookingConcurrencyTest {

    private static final int PATIENTS = 20;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private BookingSlotLockRepository slotLockRepository;

    @Test
    void exactlyOneOfTwentyConcurrentHoldsOnSameSlotSucceeds() throws Exception {
        long doctorId =
                doctorService.create(new DoctorCreateRequest("Dr. Concurrent")).id();
        LocalDateTime slot = LocalDateTime.of(2030, 2, 1, 10, 0);
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

            int successes = 0;
            int conflicts = 0;
            for (Future<BookingResponse> result : results) {
                try {
                    BookingResponse response = result.get(60, TimeUnit.SECONDS);
                    assertThat(response.status()).isEqualTo(BookingStatus.HELD);
                    successes++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BookingSlotUnavailableException.class);
                    assertThat(((BookingProblemException) e.getCause()).getStatus())
                            .isEqualTo(HttpStatus.CONFLICT);
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(PATIENTS - 1);
            assertThat(slotLockRepository.findByDoctorIdAndSlotStart(doctorId, slot))
                    .isPresent();
        } finally {
            pool.shutdownNow();
        }
    }
}
