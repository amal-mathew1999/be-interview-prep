package com.example.mockretest.booking;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingNotificationListener {

    private final BookingNotifier notifier;

    public BookingNotificationListener(BookingNotifier notifier) {
        this.notifier = notifier;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notifier.sendConfirmation(event);
    }
}
