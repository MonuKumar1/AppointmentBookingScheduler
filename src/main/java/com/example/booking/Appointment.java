package com.example.booking;

import java.time.Instant;

/**
 * An immutable reservation of a provider's time over the half-open UTC interval {@code [start, end)}.
 * Cancelling produces a replacement record via {@link #cancelled()}; instances are never mutated.
 */
public record Appointment(
        String id,
        String providerId,
        Instant start,
        Instant end,
        AppointmentStatus status,
        Instant createdAt) {

    /** Returns a copy of this appointment with status {@link AppointmentStatus#CANCELLED}. */
    public Appointment cancelled() {
        return new Appointment(id, providerId, start, end, AppointmentStatus.CANCELLED, createdAt);
    }
}
