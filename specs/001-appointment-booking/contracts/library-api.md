# Contract: Library API (service boundary)

Package `com.example.booking`. This is the only public entry point to the booking slice.
Every method is safe to call from multiple threads at once.

## `BookingService`

```java
public final class BookingService {
    public BookingService();                                        // Clock.systemUTC(), UUID ids
    public BookingService(Clock clock, Supplier<String> idGenerator); // for deterministic tests

    public BookingResult      book(String providerId, String start, String end);
    public CancellationResult cancel(String appointmentId);
    public Optional<Appointment> findById(String appointmentId);
    public List<Appointment>     listByProvider(String providerId);
}
```

### `book(providerId, start, end)`

- **Input**: `start` and `end` are ISO-8601 offset date-times with an explicit UTC offset, e.g.
  `2026-11-02T09:00:00Z`, `2026-11-02T09:00Z`, or `2026-11-02T09:00:00.000+00:00`.
- **Returns** exactly one of:
  - `BookingResult.Booked(Appointment appointment)`: stored with status `ACTIVE`.
  - `BookingResult.Conflict(String conflictingAppointmentId)`: an active appointment for the same
    provider overlaps `[start, end)`. If several do, this is the one with the earliest start.
  - `BookingResult.Invalid(String field, String reason)`: `field` ∈ {`providerId`, `start`,
    `end`}, and `reason` is one of the values in [data-model.md](../data-model.md#validation-rules).
- **Never throws** for bad input. Null arguments produce `Invalid`.
- **Atomicity**: the overlap check and the insert are one indivisible step per provider.
- **Side effects**: only on `Booked`.

### `cancel(appointmentId)`

- **Returns** exactly one of:
  - `CancellationResult.Cancelled(Appointment appointment)`: the record now has status `CANCELLED`.
  - `CancellationResult.NotFound(String appointmentId)`: no appointment with that id exists.
  - `CancellationResult.AlreadyCancelled(Appointment appointment)`: it exists but is already
    cancelled.
  - `CancellationResult.Invalid(String field, String reason)`: `field` = `appointmentId`,
    `reason` = `required`.
- **Never throws** for bad input.
- **Concurrency**: when N callers cancel the same active id at once, exactly one gets `Cancelled`
  and the rest get `AlreadyCancelled`.
- **Side effects**: only on `Cancelled`.

### `findById(appointmentId)`

Returns the appointment in any status. Returns `Optional.empty()` for an unknown, null, or blank
id.

### `listByProvider(providerId)`

Returns an immutable snapshot of all of the provider's appointments (active and cancelled), sorted
by `start` then `createdAt`. Returns an empty list for an unknown, null, or blank provider.

## Value types

```java
public enum AppointmentStatus { ACTIVE, CANCELLED }

public record Appointment(String id, String providerId, Instant start, Instant end,
                          AppointmentStatus status, Instant createdAt) {}

public sealed interface BookingResult {
    record Booked(Appointment appointment) implements BookingResult {}
    record Conflict(String conflictingAppointmentId) implements BookingResult {}
    record Invalid(String field, String reason) implements BookingResult {}
}

public sealed interface CancellationResult {
    record Cancelled(Appointment appointment) implements CancellationResult {}
    record NotFound(String appointmentId) implements CancellationResult {}
    record AlreadyCancelled(Appointment appointment) implements CancellationResult {}
    record Invalid(String field, String reason) implements CancellationResult {}
}
```

Callers are expected to use exhaustive `switch` pattern matching on the sealed results.
