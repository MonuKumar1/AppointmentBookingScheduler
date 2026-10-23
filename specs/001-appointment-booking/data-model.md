# Data Model: Conflict-Safe Appointment Booking

All instants are UTC. All intervals are half-open `[start, end)`.

## Entities

### Provider (implicit)

| Field | Type | Rules |
|---|---|---|
| `providerId` | String | Required; not blank. Opaque; no registry. Created implicitly on first booking. |

Internally each provider has a **ProviderSchedule**: its own lock (monitor) and a map of all of
its appointments by id, active and cancelled.

### Appointment

Immutable record. Cancellation produces a replacement record.

| Field | Type | Rules |
|---|---|---|
| `id` | String | Generated (UUID) on successful booking; unique. |
| `providerId` | String | Not blank. |
| `start` | `Instant` | Parsed from explicit-UTC ISO-8601 (see Validation). |
| `end` | `Instant` | Parsed likewise; `end > start` (strictly). |
| `status` | `AppointmentStatus` | `ACTIVE` on creation. |
| `createdAt` | `Instant` | From the injected clock at booking time. |

### AppointmentStatus

`ACTIVE` | `CANCELLED`

### Results (sealed outcomes)

These names are canonical: code, CLI output, tests, and docs MUST use exactly these (Principle V).

| Operation | Outcome | Payload | CLI keyword |
|---|---|---|---|
| book | `Booked` | the stored `Appointment` | `BOOKED` |
| book | `Conflict` | `conflictingAppointmentId` (earliest-starting active overlap) | `CONFLICT` |
| book | `Invalid` | `field`, `reason` | `INVALID` |
| cancel | `Cancelled` | the updated `Appointment` (status `CANCELLED`) | `CANCELLED` |
| cancel | `NotFound` | `appointmentId` | `NOT_FOUND` |
| cancel | `AlreadyCancelled` | the existing `Appointment` | `ALREADY_CANCELLED` |
| cancel | `Invalid` | `field`, `reason` | `INVALID` |

## Validation rules

Checked in this order; the first failure is returned. All checks finish before any state is
touched (FR-010).

| # | Field | Rejected when | `reason` |
|---|---|---|---|
| 1 | `providerId` (book) / `appointmentId` (cancel) | null, empty, or whitespace-only | `required` |
| 2 | `start` | null/blank | `required` |
| 2 | `start` | not ISO-8601 date-time with offset (includes zoneless input) | `invalid timestamp` |
| 2 | `start` | offset ≠ UTC (anything other than `Z`, `+00:00`, `-00:00`) | `must be UTC` |
| 3 | `end` | same three rules as `start` | same |
| 4 | `end` | `end <= start` | `must be after start` |

## Booking invariant

For every provider P, at every observable moment, there are no two appointments a and b with
`a.status == ACTIVE`, `b.status == ACTIVE`, `a.providerId == b.providerId == P`, `a.id != b.id`,
and `a.start < b.end && b.start < a.end`.

Guaranteed by the following: the overlap scan, the insert into P's schedule, and the index write
all happen while P's monitor is held.

## State transitions

```text
            book (valid, no overlap)
  (none) ───────────────────────────────▶ ACTIVE
                                            │
                                            │ cancel
                                            ▼
                                        CANCELLED ──cancel──▶ (unchanged; AlreadyCancelled)
```

- No transition out of `CANCELLED`; rebooking creates a **new** appointment with a new id.
- Every rejected operation (Invalid, Conflict, NotFound, AlreadyCancelled) causes no transition and
  no write.

## Queries

- `findById(id)` returns the appointment if known, in any status.
- `listByProvider(providerId)` returns all of a provider's appointments (active and cancelled),
  sorted by `start`, then `createdAt`. It returns an empty list for an unknown provider.
