package com.example.mockretest.booking;

import com.example.mockretest.booking.dto.BookingDoctorCreateRequest;
import com.example.mockretest.booking.dto.BookingDoctorResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingDoctorService {

    private final BookingDoctorRepository doctorRepository;
    private final BookingSlotLockRepository slotLockRepository;
    private final BookingSlotPolicy slotPolicy;
    private final BookingClock clock;

    public BookingDoctorService(
            BookingDoctorRepository doctorRepository,
            BookingSlotLockRepository slotLockRepository,
            BookingSlotPolicy slotPolicy,
            BookingClock clock) {
        this.doctorRepository = doctorRepository;
        this.slotLockRepository = slotLockRepository;
        this.slotPolicy = slotPolicy;
        this.clock = clock;
    }

    @Transactional
    public BookingDoctorResponse create(BookingDoctorCreateRequest request) {
        BookingDoctor doctor =
                doctorRepository.save(new BookingDoctor(request.name().strip()));
        return toResponse(doctor);
    }

    @Transactional(readOnly = true)
    public BookingDoctorResponse find(long doctorId) {
        return doctorRepository
                .findById(doctorId)
                .map(BookingDoctorService::toResponse)
                .orElseThrow(() -> new BookingDoctorNotFoundException(doctorId));
    }

    /** Future slot starts on {@code date} that are neither confirmed nor covered by an unexpired hold. */
    @Transactional(readOnly = true)
    public List<LocalDateTime> availableSlots(long doctorId, LocalDate date) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new BookingDoctorNotFoundException(doctorId);
        }
        Instant now = clock.instant();
        LocalDateTime localNow = clock.localNow();
        Set<LocalDateTime> taken = slotLockRepository
                .findByDoctorIdAndSlotStartGreaterThanEqualAndSlotStartLessThan(
                        doctorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .filter(lock -> !lock.isExpired(now))
                .map(BookingSlotLock::getSlotStart)
                .collect(Collectors.toSet());
        return slotPolicy.slotsFor(date).stream()
                .filter(slot -> !slot.isBefore(localNow))
                .filter(slot -> !taken.contains(slot))
                .toList();
    }

    private static BookingDoctorResponse toResponse(BookingDoctor doctor) {
        return new BookingDoctorResponse(doctor.getId(), doctor.getName());
    }
}
