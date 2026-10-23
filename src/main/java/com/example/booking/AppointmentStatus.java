package com.example.booking;

/** Lifecycle state of an appointment. Only {@link #ACTIVE} appointments block a provider's time. */
public enum AppointmentStatus {
    ACTIVE,
    CANCELLED
}
