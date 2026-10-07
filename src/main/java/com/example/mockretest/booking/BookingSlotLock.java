package com.example.mockretest.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * The single active claim on a doctor's slot. The unique {@code (doctor_id, slot_start)} constraint makes the
 * database the arbiter against double booking. A row with a non-null {@code expiresAt} is a hold; a row with a null
 * {@code expiresAt} is a confirmed booking. Expired holds and cancellations delete the row.
 */
@Entity
@Table(
        name = "clinic_slot_lock",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_clinic_slot_lock_doctor_slot",
                    columnNames = {"doctor_id", "slot_start"}),
            @UniqueConstraint(name = "uk_clinic_slot_lock_booking", columnNames = "booking_id")
        })
public class BookingSlotLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    private long version;

    protected BookingSlotLock() {}

    public BookingSlotLock(Long doctorId, LocalDateTime slotStart, Long bookingId, Instant expiresAt) {
        this.doctorId = doctorId;
        this.slotStart = slotStart;
        this.bookingId = bookingId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    void markConfirmed() {
        expiresAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
