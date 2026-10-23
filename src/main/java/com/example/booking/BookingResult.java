package com.example.booking;

/** Outcome of {@link BookingService#book}. Exactly one of the permitted records. */
public sealed interface BookingResult {

    /** The appointment was stored with status {@code ACTIVE}. */
    record Booked(Appointment appointment) implements BookingResult {}

    /** An active appointment for the same provider overlaps; it is the earliest-starting one. */
    record Conflict(String conflictingAppointmentId) implements BookingResult {}

    /** The request was rejected before any state was touched. */
    record Invalid(String field, String reason) implements BookingResult {}
}
