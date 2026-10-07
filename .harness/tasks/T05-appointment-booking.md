---
id: T05
title: Clinic appointment booking with holds
branch: feature/q5-booking
depends_on: []
owns:
  - src/main/java/com/example/mockretest/booking/**
  - src/test/java/com/example/mockretest/booking/**
  - src/main/resources/booking.properties
---

## Goal
Booking API where patients hold and confirm 30-minute slots with doctors, safely under concurrency.

## Acceptance criteria
- AC1: Doctors exist (seeded or via `POST /api/doctors` `{"name": "..."}` → `201`). Each doctor has 30-minute slots within working hours (default 09:00–17:00, configurable).
- AC2: `GET /api/doctors/{doctorId}/slots?date=yyyy-MM-dd` → `200` list of **available** slot start times for that day (excludes confirmed bookings and unexpired holds). Unknown doctor → `404`; missing/invalid date → `400`.
- AC3: `POST /api/bookings/hold` `{"doctorId", "patientId", "start"}` → `201` with `bookingId`, `status: HELD`, `expiresAt` (= now + 5 min, configurable). `start` not aligned to a 30-minute boundary within working hours → `400`. Slot already held (unexpired) or confirmed → `409`.
- AC4: `POST /api/bookings/{bookingId}/confirm` (body `{"patientId"}`) → `200` `status: CONFIRMED`. Confirming an expired hold → `409` (or `410`) and the booking is not confirmed. Wrong patient → `403`. Already confirmed → `409`.
- AC5: A hold not confirmed within the hold timeout expires and the slot becomes available again (visible in AC2 and holdable by another patient), regardless of whether a background cleanup has run yet.
- AC6: A slot can never be double-booked: when 20 patients try to hold the same slot at the same moment, exactly 1 succeeds and 19 get `409`. Guaranteed by the database (unique constraint / locking), not just an in-memory check.
- AC7: `POST /api/bookings/{bookingId}/cancel` (body `{"patientId"}`) on a confirmed booking → `200` `status: CANCELLED` and the slot becomes available again; the same slot can then be held again.
- AC8: On confirm, a notification is sent (log line is enough) **asynchronously** — the confirm response doesn't wait for it — and **only after** the booking is committed (if the transaction rolls back, nothing is sent).
- AC9: All errors use `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`).
- AC10: Automated tests: 20 concurrent holds on one slot → exactly 1 success; expired hold frees the slot (controllable clock, no long sleeps); cancel frees the slot; notification fires only after commit and not on rollback.

## Out of scope
- Patient registry, real email/SMS, any other feature package, `application.properties`, `pom.xml`.

## Implementation notes
- Package `com.example.mockretest.booking`; entities `Doctor` (`clinic_doctor`), `Booking` (`clinic_booking`).
- Double-booking: a `SlotLock`/active-slot row with unique `(doctor_id, slot_start)` that is deleted/replaced on expiry/cancel; or `Booking` with unique `(doctor_id, slot_start, active_key)`. Expired holds must be lazily reclaimed in the same transaction as the new hold (delete expired row for that slot, then insert) so AC5 holds without the scheduler; a `@Scheduled` cleanup is optional.
- Publish a `BookingConfirmedEvent`; listener `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`. `@EnableAsync`/`@EnableScheduling` on `BookingConfig`. Clock bean `bookingClock`.
- Concurrency test: `ExecutorService` + `CountDownLatch` start gate, 20 tasks through the service or MockMvc.
