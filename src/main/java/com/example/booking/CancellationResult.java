package com.example.booking;

/** Outcome of {@link BookingService#cancel}. Exactly one of the permitted records. */
public sealed interface CancellationResult {

    /** The appointment was active and is now {@code CANCELLED}. */
    record Cancelled(Appointment appointment) implements CancellationResult {}

    /** No appointment with this id exists. */
    record NotFound(String appointmentId) implements CancellationResult {}

    /** The appointment exists but was already cancelled; nothing changed. */
    record AlreadyCancelled(Appointment appointment) implements CancellationResult {}

    /** The request was rejected before any state was touched. */
    record Invalid(String field, String reason) implements CancellationResult {}
}
