package com.example.mockretest.booking;

import com.example.mockretest.booking.dto.BookingDoctorCreateRequest;
import com.example.mockretest.booking.dto.BookingDoctorResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/doctors")
public class BookingDoctorController {

    private final BookingDoctorService doctorService;

    public BookingDoctorController(BookingDoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @PostMapping
    public ResponseEntity<BookingDoctorResponse> create(@Valid @RequestBody BookingDoctorCreateRequest request) {
        BookingDoctorResponse created = doctorService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{doctorId}")
    public BookingDoctorResponse get(@PathVariable long doctorId) {
        return doctorService.find(doctorId);
    }

    @GetMapping("/{doctorId}/slots")
    public List<LocalDateTime> availableSlots(
            @PathVariable long doctorId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return doctorService.availableSlots(doctorId, date);
    }
}
