# Assumptions & Invariant Note

## The invariant

A provider never holds two overlapping **active** appointments. Appointments are half-open
intervals `[start, end)`, so back-to-back appointments (one ends at the exact instant the next
begins) are allowed. Appointments for different providers never conflict. The check and the insert
are atomic per provider (see [design.md §4](design.md#4-concurrency-approach)).

## Time

- **All timestamps are UTC.** Only an explicit zero offset is accepted: `Z`, `+00:00`, or `-00:00`.
  Any other offset (e.g. `+05:30`) is rejected with `must be UTC`, and a timestamp with no zone is
  rejected with `invalid timestamp`. **Nothing is ever converted.**
- The format is ISO-8601 date-time with an offset, e.g. `2026-11-02T09:00:00Z`. Seconds and fractional
  seconds (up to nanoseconds) are optional, and comparisons use full precision.
- Booking in the past is allowed. There is no "now" check, which keeps behaviour deterministic and
  testable; this is listed in [limitations](limitations.md).
- There is no minimum or maximum duration and no slot grid. The only rule is `end > start`.

## Identifiers

- A booking needs only a **provider id**; there is no client or customer id (clarification Q1).
  Cancelling needs only the **appointment id**.
- An id is "missing" if it is null, empty, or whitespace-only. Missing ids produce `Invalid(…, required)`.
- Providers are implicit: any non-blank id is accepted and created on first booking.
- Ids are matched exactly as given: they are not trimmed or case-folded, so `" provider-1"` and
  `"Provider-1"` are different providers from `"provider-1"` (limitation L12).
- Appointment ids are random UUIDs. The CLI `demo` mode and the tests use sequential ids (`appt-1`, …)
  for readability and determinism.

## State

- Everything is in memory in one JVM process. Data is lost on restart.
- Cancelled appointments are kept with status `CANCELLED`, not deleted. This is how
  `AlreadyCancelled` is told apart from `NotFound`. Rebooking a freed slot creates a new appointment
  with a new id.
- Every rejected request (`Invalid`, `Conflict`, `NotFound`, `AlreadyCancelled`) leaves stored
  appointments exactly as they were.

## Callers

- All callers are trusted. There is no authentication or authorisation, so anyone with an
  appointment id can cancel it.
- The library may be called from any number of threads at once, on platform or virtual threads.

## Out of scope

Frontend, authentication, notifications, recurring availability, calendar integration, deployment,
real databases, and any time-zone handling beyond UTC.
