package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.example.mockretest.booking.dto.BookingHoldRequest;
import com.example.mockretest.booking.dto.BookingPatientRequest;
import com.example.mockretest.booking.dto.BookingResponse;
import com.example.mockretest.booking.dto.DoctorCreateRequest;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class BookingNotificationTest {

    private static final long WAIT_MS = 5_000;

    @MockitoSpyBean
    private BookingNotifier notifier;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long bookingId;

    @BeforeEach
    void holdSlot() {
        long doctorId =
                doctorService.create(new DoctorCreateRequest("Dr. Notify")).id();
        bookingId = bookingService
                .hold(new BookingHoldRequest(doctorId, "p1", LocalDateTime.of(2030, 3, 1, 11, 0)))
                .bookingId();
    }

    @AfterEach
    void resetSpy() {
        reset(notifier);
    }

    @Test
    void notificationIsSentOnlyAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            bookingService.confirm(bookingId, new BookingPatientRequest("p1"));
            verify(notifier, after(300).never()).sendConfirmation(any());
        });

        ArgumentCaptor<BookingConfirmedEvent> event = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(notifier, timeout(WAIT_MS)).sendConfirmation(event.capture());
        assertThat(event.getValue().bookingId()).isEqualTo(bookingId);
        assertThat(event.getValue().patientId()).isEqualTo("p1");
    }

    @Test
    void noNotificationWhenTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            bookingService.confirm(bookingId, new BookingPatientRequest("p1"));
            tx.setRollbackOnly();
        });

        verify(notifier, after(500).never()).sendConfirmation(any());
        assertThat(bookingService.find(bookingId).status()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void confirmDoesNotWaitForNotification() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        doAnswer(invocation -> {
                    release.await(WAIT_MS, TimeUnit.MILLISECONDS);
                    delivered.countDown();
                    return null;
                })
                .when(notifier)
                .sendConfirmation(any());

        BookingResponse response = bookingService.confirm(bookingId, new BookingPatientRequest("p1"));

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(delivered.getCount()).isEqualTo(1);
        verify(notifier, timeout(WAIT_MS)).sendConfirmation(any());
        release.countDown();
        assertThat(delivered.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }
}
