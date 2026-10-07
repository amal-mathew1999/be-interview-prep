package com.example.mockretest.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A patient's booking of one doctor slot. Rows are kept as history; exclusivity of a slot is enforced by
 * {@link BookingSlotLock}.
 */
@Entity
@Table(
        name = "clinic_booking",
        indexes = @Index(name = "ix_clinic_booking_doctor_slot", columnList = "doctor_id, slot_start"))
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Version
    private long version;

    protected Booking() {}

    private Booking(Long doctorId, String patientId, LocalDateTime slotStart, Instant holdExpiresAt) {
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.slotStart = slotStart;
        this.holdExpiresAt = holdExpiresAt;
        this.status = BookingStatus.HELD;
    }

    public static Booking hold(Long doctorId, String patientId, LocalDateTime slotStart, Instant holdExpiresAt) {
        return new Booking(doctorId, patientId, slotStart, holdExpiresAt);
    }

    public boolean isHoldExpired(Instant now) {
        return status == BookingStatus.HELD && !now.isBefore(holdExpiresAt);
    }

    /** Status as observed at {@code now}: an unconfirmed hold past its expiry reads as EXPIRED. */
    public BookingStatus effectiveStatus(Instant now) {
        return isHoldExpired(now) ? BookingStatus.EXPIRED : status;
    }

    public boolean isOwnedBy(String candidatePatientId) {
        return patientId.equals(candidatePatientId);
    }

    void confirm() {
        status = BookingStatus.CONFIRMED;
    }

    void cancel() {
        status = BookingStatus.CANCELLED;
    }

    void expire() {
        status = BookingStatus.EXPIRED;
    }

    public Long getId() {
        return id;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public String getPatientId() {
        return patientId;
    }

    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }
}
