# Design: Conflict-Safe Appointment Booking

This is the design spec for the booking slice. Detailed sources:
[spec](../specs/001-appointment-booking/spec.md),
[plan](../specs/001-appointment-booking/plan.md),
[research decisions R1–R9](../specs/001-appointment-booking/research.md),
[data model](../specs/001-appointment-booking/data-model.md), and the
[library](../specs/001-appointment-booking/contracts/library-api.md) and
[CLI](../specs/001-appointment-booking/contracts/cli.md) contracts.

## 1. Service boundary

```text
 caller (tests, other code, threads) ──▶ BookingService  ◀── BookingCli (stdin/stdout, demo)
                                          │  validation → per-provider lock → in-memory maps
```

- **`com.example.booking.BookingService`** is the service boundary. It is an in-process, thread-safe
  Java library with four operations: `book`, `cancel`, `findById`, and `listByProvider`. All
  business rules live here.
- **`com.example.booking.cli.BookingCli`** is a thin wrapper. It parses one command per line, calls
  the service, and prints one result line. It contains no booking logic, and it depends on the
  library, never the reverse.
- No network, database, or filesystem is used at runtime, and there are no runtime dependencies.
  The library is built with Java 21 (`maven.compiler.release=21`); JUnit 5 is used for tests only.

## 2. Data and API design

**Inputs are strings.** `book(String providerId, String start, String end)` takes ISO-8601
timestamps as text, so the slice itself rejects malformed or non-UTC input (research R2). With an
`Instant` parameter, that validation would happen in callers and could not be tested here.

**Entities.** `Appointment` is an immutable record: `id`, `providerId`, `start`, `end`, `status`
(`ACTIVE` or `CANCELLED`), and `createdAt`. Cancelling creates a replacement record; records are
never mutated. Providers are implicit: any non-blank id, created on its first booking.

**Results are sealed types, not exceptions.** Every outcome is one of these canonical names, used
identically in code, CLI output, tests, and docs:

| Operation | Outcomes | CLI keyword |
|---|---|---|
| `book` | `Booked(appointment)`, `Conflict(conflictingAppointmentId)`, `Invalid(field, reason)` | `BOOKED`, `CONFLICT`, `INVALID` |
| `cancel` | `Cancelled(appointment)`, `NotFound(appointmentId)`, `AlreadyCancelled(appointment)`, `Invalid(field, reason)` | `CANCELLED`, `NOT_FOUND`, `ALREADY_CANCELLED`, `INVALID` |

Validation `reason` values are `required`, `invalid timestamp`, `must be UTC`, and
`must be after start`. They are checked in a fixed order (provider/appointment id → start → end →
duration), and the first failure is returned.

**Storage.** There are two maps:
- `ConcurrentHashMap<String, ProviderSchedule>`, keyed by provider. Each schedule holds all of that
  provider's appointments.
- `ConcurrentHashMap<String, Appointment>`, keyed by appointment id, for lookups.

## 3. Booking invariant

> For every provider P, at every observable moment, no two appointments a ≠ b of P are both
> `ACTIVE` with `a.start < b.end && b.start < a.end`.

- Intervals are half-open `[start, end)`. An appointment ending at 10:00 and one starting at
  10:00 do not overlap. Comparisons use full nanosecond precision.
- Only appointments of the **same** provider conflict, and only **active** ones count. A cancelled
  appointment never blocks its slot.
- If several active appointments overlap a request, the conflict reports the one with the earliest
  start, so results are deterministic.
- Any rejected request (`Invalid`, `Conflict`, `NotFound`, `AlreadyCancelled`) writes nothing.
  Validation runs before any lock is taken or any schedule is created.

The tests check the invariant independently: `InvariantAssertions` re-implements the overlap test
without calling production code, and runs after every test case.

## 4. Concurrency approach

**One lock per provider.** `book` gets (or atomically creates, with `computeIfAbsent`) the
provider's `ProviderSchedule`, then runs `synchronized (schedule)` around three steps: the overlap
scan, the insert into the schedule, and the write to the id index. No thread can get between the
check and the insert for that provider:

```text
Thread A: lock(provider-1) → scan: free → insert appt-1 → unlock
Thread B:      waits ...                                  lock(provider-1) → scan: sees appt-1 → CONFLICT
```

- **Cancel re-reads under the lock.** `cancel` looks the id up, takes the owning provider's lock,
  then reads the record again before deciding. Two simultaneous cancels therefore give exactly one
  `Cancelled` and one `AlreadyCancelled`, never two writes.
- **Reads.** `findById` is lock-free and safe, because records are immutable and published through
  a `ConcurrentHashMap`. `listByProvider` takes a snapshot under the provider's lock.
- **Different providers never wait for each other**, because each has its own lock.
- **Threads.** The library creates no threads. It runs on the caller's thread, whether that is a
  platform thread, a pool thread, or a virtual thread. The tests use a fixed pool of 16 platform
  threads and a `CountDownLatch` start gate, and repeat the race 100 times
  (TC-050 to TC-053; see [test plan](test-plan.md)).

## 5. Tradeoffs

| Choice | Alternative | Why this choice |
|---|---|---|
| Lock per provider (`synchronized`) | One global lock | Equally correct and slightly less code, but a global lock serialises unrelated providers. Per-provider locking costs one extra map. |
| `synchronized` | `ReentrantLock` | The block is a few microseconds of in-memory work with no I/O. `ReentrantLock` would avoid virtual-thread *pinning* on JDK 21–23 (fixed in JDK 24, JEP 491), but pinning such a short block is harmless, and `synchronized` cannot leak an unreleased lock. |
| Pessimistic locking | Optimistic copy-on-write + CAS retry | Harder to verify by reading, and no benefit at this scale. |
| Linear scan of a provider's appointments | `TreeMap` keyed by start (O(log n)) | Easiest to verify; O(n) is negligible at exercise scale. Recorded as a follow-up in [limitations](limitations.md). |
| Strings at the boundary | `Instant` parameters | Keeps all validation, including "must be UTC", inside the slice and testable there. |
| Strict UTC: reject non-zero offsets | Convert offsets to UTC | The brief puts any time-zone handling beyond UTC out of scope. Converting would hide caller mistakes. `Instant.parse` is avoided because it converts silently. |
| Sealed result types | Exceptions | Outcomes are values that callers must handle exhaustively (`switch`), and they are easy to compare in tests. |
| Lock taken from the first version | Build unlocked first to demonstrate the race | Human decision D1-a (2026-10-23): the invariant must hold at every step. Concurrency tests were still written first and seen failing (red because the methods did not exist yet). |
