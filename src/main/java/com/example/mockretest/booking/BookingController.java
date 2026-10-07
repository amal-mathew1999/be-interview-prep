package com.example.mockretest.booking;

import com.example.mockretest.booking.dto.BookingHoldRequest;
import com.example.mockretest.booking.dto.BookingPatientRequest;
import com.example.mockretest.booking.dto.BookingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Places a hold. There is deliberately no {@code GET /api/bookings/{id}} (and hence no {@code Location}): bookings
     * are only visible to their owner through the responses of hold/confirm/cancel.
     */
    @PostMapping("/hold")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@Valid @RequestBody BookingHoldRequest request) {
        return bookingService.hold(request);
    }

    @PostMapping("/{bookingId}/confirm")
    public BookingResponse confirm(@PathVariable long bookingId, @Valid @RequestBody BookingPatientRequest request) {
        return bookingService.confirm(bookingId, request);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable long bookingId, @Valid @RequestBody BookingPatientRequest request) {
        return bookingService.cancel(bookingId, request);
    }
}
