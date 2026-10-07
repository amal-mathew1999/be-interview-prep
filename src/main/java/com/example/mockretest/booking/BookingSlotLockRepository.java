package com.example.mockretest.booking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSlotLockRepository extends JpaRepository<BookingSlotLock, Long> {

    Optional<BookingSlotLock> findByDoctorIdAndSlotStart(Long doctorId, LocalDateTime slotStart);

    Optional<BookingSlotLock> findByBookingId(Long bookingId);

    /** Locks whose slot start lies in {@code [from, to]} (both inclusive). */
    List<BookingSlotLock> findByDoctorIdAndSlotStartBetween(Long doctorId, LocalDateTime from, LocalDateTime to);
}
