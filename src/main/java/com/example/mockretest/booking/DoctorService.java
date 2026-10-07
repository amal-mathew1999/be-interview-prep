package com.example.mockretest.booking;

import com.example.mockretest.booking.dto.DoctorCreateRequest;
import com.example.mockretest.booking.dto.DoctorResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final BookingSlotLockRepository slotLockRepository;
    private final BookingSlotPolicy slotPolicy;
    private final Clock clock;

    public DoctorService(
            DoctorRepository doctorRepository,
            BookingSlotLockRepository slotLockRepository,
            BookingSlotPolicy slotPolicy,
            @Qualifier("bookingClock") Clock clock) {
        this.doctorRepository = doctorRepository;
        this.slotLockRepository = slotLockRepository;
        this.slotPolicy = slotPolicy;
        this.clock = clock;
    }

    @Transactional
    public DoctorResponse create(DoctorCreateRequest request) {
        Doctor doctor = doctorRepository.save(new Doctor(request.name().strip()));
        return toResponse(doctor);
    }

    @Transactional(readOnly = true)
    public DoctorResponse find(long doctorId) {
        return doctorRepository
                .findById(doctorId)
                .map(DoctorService::toResponse)
                .orElseThrow(() -> new BookingDoctorNotFoundException(doctorId));
    }

    /** Slot starts on {@code date} that are neither confirmed nor covered by an unexpired hold. */
    @Transactional(readOnly = true)
    public List<LocalDateTime> availableSlots(long doctorId, LocalDate date) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new BookingDoctorNotFoundException(doctorId);
        }
        Instant now = clock.instant();
        Set<LocalDateTime> taken = slotLockRepository
                .findByDoctorIdAndSlotStartGreaterThanEqualAndSlotStartLessThan(
                        doctorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .filter(lock -> !lock.isExpired(now))
                .map(BookingSlotLock::getSlotStart)
                .collect(Collectors.toSet());
        return slotPolicy.slotsFor(date).stream()
                .filter(slot -> !taken.contains(slot))
                .toList();
    }

    private static DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getName());
    }
}
