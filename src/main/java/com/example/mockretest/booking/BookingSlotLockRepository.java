package com.example.mockretest.booking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSlotLockRepository extends JpaRepository<BookingSlotLock, Long> {

    Optional<BookingSlotLock> findByDoctorIdAndSlotStart(Long doctorId, LocalDateTime slotStart);

    Optional<BookingSlotLock> findByBookingId(Long bookingId);

    List<BookingSlotLock> findByDoctorIdAndSlotStartGreaterThanEqualAndSlotStartLessThan(
            Long doctorId, LocalDateTime from, LocalDateTime to);
}
