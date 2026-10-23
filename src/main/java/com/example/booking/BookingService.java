package com.example.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The service boundary of the booking slice. Books and cancels appointments while guaranteeing that
 * a provider never holds two overlapping ACTIVE appointments. Safe to call from multiple threads.
 */
public final class BookingService {

    static final String MUST_BE_AFTER_START = "must be after start";

    private static final Comparator<Appointment> BY_START_THEN_CREATED =
            Comparator.comparing(Appointment::start).thenComparing(Appointment::createdAt);

    private final ConcurrentHashMap<String, ProviderSchedule> schedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Appointment> byId = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public BookingService() {
        this(Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    public BookingService(Clock clock, Supplier<String> idGenerator) {
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public BookingResult book(String providerId, String start, String end) {
        // Validation completes before any state is read for mutation, so a rejection changes nothing.
        if (isBlank(providerId)) {
            return new BookingResult.Invalid("providerId", UtcTimestamps.REQUIRED);
        }
        Instant newStart;
        Instant newEnd;
        try {
            newStart = UtcTimestamps.parse(start);
        } catch (UtcTimestamps.InvalidTimestamp e) {
            return new BookingResult.Invalid("start", e.reason());
        }
        try {
            newEnd = UtcTimestamps.parse(end);
        } catch (UtcTimestamps.InvalidTimestamp e) {
            return new BookingResult.Invalid("end", e.reason());
        }
        if (!newEnd.isAfter(newStart)) {
            return new BookingResult.Invalid("end", MUST_BE_AFTER_START);
        }

        ProviderSchedule schedule = schedules.computeIfAbsent(providerId, id -> new ProviderSchedule());
        // The overlap check and the insert form one critical section per provider (FR-012).
        synchronized (schedule) {
            Optional<Appointment> conflict = schedule.appointments.values().stream()
                    .filter(a -> a.status() == AppointmentStatus.ACTIVE)
                    .filter(a -> newStart.isBefore(a.end()) && a.start().isBefore(newEnd))
                    .min(BY_START_THEN_CREATED);
            if (conflict.isPresent()) {
                return new BookingResult.Conflict(conflict.get().id());
            }
            Appointment appointment = new Appointment(idGenerator.get(), providerId, newStart, newEnd,
                    AppointmentStatus.ACTIVE, Instant.now(clock));
            schedule.appointments.put(appointment.id(), appointment);
            byId.put(appointment.id(), appointment);
            return new BookingResult.Booked(appointment);
        }
    }

    public CancellationResult cancel(String appointmentId) {
        if (isBlank(appointmentId)) {
            return new CancellationResult.Invalid("appointmentId", UtcTimestamps.REQUIRED);
        }
        Appointment found = byId.get(appointmentId);
        if (found == null) {
            return new CancellationResult.NotFound(appointmentId);
        }
        ProviderSchedule schedule = schedules.get(found.providerId());
        synchronized (schedule) {
            // Re-read under the lock: a concurrent cancel may have won since the lookup above.
            Appointment current = byId.get(appointmentId);
            if (current.status() == AppointmentStatus.CANCELLED) {
                return new CancellationResult.AlreadyCancelled(current);
            }
            Appointment cancelled = current.cancelled();
            schedule.appointments.put(appointmentId, cancelled);
            byId.put(appointmentId, cancelled);
            return new CancellationResult.Cancelled(cancelled);
        }
    }

    public Optional<Appointment> findById(String appointmentId) {
        if (isBlank(appointmentId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(appointmentId));
    }

    public List<Appointment> listByProvider(String providerId) {
        if (isBlank(providerId)) {
            return List.of();
        }
        ProviderSchedule schedule = schedules.get(providerId);
        if (schedule == null) {
            return List.of();
        }
        synchronized (schedule) {
            return schedule.appointments.values().stream().sorted(BY_START_THEN_CREATED).toList();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** All appointments of one provider, active and cancelled. Its monitor guards check-and-write. */
    private static final class ProviderSchedule {
        private final Map<String, Appointment> appointments = new LinkedHashMap<>();
    }
}
