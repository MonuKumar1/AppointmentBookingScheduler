# Research & Decisions: Conflict-Safe Appointment Booking

Phase 0 output. Each entry gives the decision, why it was made, and what else was considered.

## R1. Language, build, and test stack

- **Decision**: Java, compiled with the installed JDK 23 and `maven.compiler.release=21`. Maven 3.9
  builds it and JUnit Jupiter 5 (imported via `junit-bom`) tests it, run by Maven Surefire 3.x.
- **Rationale**: chosen by the user on 2026-10-23. `java.util.concurrent` gives first-class tools
  for the concurrency requirement (FR-012), such as `ExecutorService` and `CountDownLatch`.
  Targeting `--release 21` (LTS) means reviewers with any JDK 21+ can build it, and the compiler
  rejects any API newer than 21. Records, sealed interfaces, and switch pattern matching are all
  final in 21.
- **Environment note**: on the development machine, `java` on PATH is JDK 23, but Maven runs on a
  Homebrew JDK 25. With `release=21`, the produced bytecode is the same either way.
- **Alternatives considered**: installing JDK 21 (unnecessary given `--release`); targeting 25 LTS
  (would exclude reviewers on 21); Python 3.11 + pytest (less code, weaker concurrency showcase);
  TypeScript/Node (single-threaded, so the concurrency test would prove little).

## R2. Service boundary and input types

- **Decision**: the public API is `BookingService` with methods
  `book(String providerId, String start, String end)`, `cancel(String appointmentId)`,
  `findById(String)`, and `listByProvider(String)`. Timestamps cross the boundary as ISO-8601
  **strings**.
- **Rationale**: the spec asks for invalid timestamps to be rejected (FR-009, User Story 3).
  If the API took `Instant`, malformed and non-UTC input would be rejected by the caller's parsing
  code, not by the slice, and could not be tested at the boundary. Strings keep all validation
  inside the service, and the CLI stays a pure pass-through.
- **Alternatives considered**: a typed `Instant` API plus a separate parser (splits validation
  across two layers); exceptions for rejections (the spec wants distinct, inspectable results, and
  exceptions make "store unchanged" harder to reason about).

## R3. Concurrency: atomic check-and-insert

- **Decision**: per-provider locking. `ConcurrentHashMap<String, ProviderSchedule>` maps each
  provider to its schedule, created atomically with `computeIfAbsent`. `book` and `cancel` take
  `synchronized (schedule)` around read-check-write. A global `ConcurrentHashMap<String, Appointment>`
  index by id serves lookups and is only written while the owning provider's lock is held.
  `Appointment` is an immutable record, so cancelling replaces the record instead of mutating it.
- **Rationale**: this makes the check and the insert indivisible for a provider (FR-012), so no
  interleaving can produce overlapping active appointments. Different providers use different
  monitors, which reflects "different providers never conflict" at runtime too. Immutable records
  make lock-free reads (`findById`) safe.
- **Cancel race**: `cancel` reads the id from the index, then takes that provider's lock and
  **re-reads the record under the lock** before deciding between Cancelled and AlreadyCancelled.
  So two concurrent cancels yield exactly one Cancelled.
- **Alternatives considered**:
  - A single global lock (`synchronized` methods): equally correct and slightly less code, but it
    serialises unrelated providers. Kept as the documented fallback in `docs/design.md`.
  - Optimistic concurrency (copy-on-write list + CAS retry): more subtle to verify, with no benefit
    at this scale.
  - `ReentrantLock` per provider: needed only for `tryLock` or timeouts, which the spec does not
    require. Intrinsic monitors are simpler.
  - Single-threaded actor/queue: correct, but adds an executor lifecycle to a library.

## R4. Overlap detection algorithm

- **Decision**: a linear scan over the provider's appointments, filtered to `ACTIVE`, using the
  half-open test `newStart < existing.end && existing.start < newEnd` (FR-003). If several
  appointments conflict, report the one with the earliest start, so results are deterministic.
- **Rationale**: easiest to verify by inspection, and O(n) per provider is negligible at exercise
  scale (Principle III).
- **Alternatives considered**: a `TreeMap` keyed by start, using `lowerEntry` and `ceilingEntry`
  (O(log n), valid because active intervals for a provider are disjoint). This is recorded as a
  follow-up in `docs/limitations.md`.

## R5. Strict UTC timestamp parsing

- **Decision**: parse with `DateTimeFormatter.ISO_OFFSET_DATE_TIME` into `OffsetDateTime`, then
  require `getOffset().equals(ZoneOffset.UTC)`, and store as `Instant`. `Z`, `+00:00`, and
  `-00:00` are accepted, along with optional seconds and fractional seconds up to nanoseconds.
  A missing zone fails the parse. Any non-zero offset is rejected with reason "must be UTC".
- **Rationale**: this implements FR-005 exactly: explicit UTC only, no conversion. Error reasons
  separate "unparseable" from "not UTC", which helps callers.
- **Alternatives considered**: `Instant.parse` (since JDK 12 it accepts any offset and silently
  converts, which violates FR-005); a custom regex (re-implements ISO-8601, which is error-prone).

## R6. Identifiers and time source

- **Decision**: appointment ids are `UUID.randomUUID().toString()`. `createdAt` comes from an
  injected `java.time.Clock`, which defaults to `Clock.systemUTC()`. `BookingService` has a
  constructor that takes `Clock` and `Supplier<String>` id generator, so tests are deterministic.
- **Rationale**: UUIDs are unique without coordination, which suits concurrent booking.
  Injection keeps tests reproducible without mocks.
- **Alternatives considered**: an `AtomicLong` sequence (fine too, but it reveals booking volume
  and order).

## R7. Validation order and reporting

- **Decision**: validate `providerId`, then `start`, then `end`, then duration (`end > start`).
  Return the **first** failure as `Invalid(field, reason)`. Blank means null, empty, or only
  whitespace (`String.isBlank`). Validation completes before any lock is taken or any state is
  read for mutation.
- **Rationale**: the order is deterministic, so tests and docs can assert exact results.
  Validating before locking means nothing can change on a rejection (FR-010).
- **Alternatives considered**: collecting all errors at once (nicer UX, but more result surface
  than the spec asks for).

## R8. CLI shape

- **Decision**: `BookingCli` reads one command per line from stdin and prints one result line per
  command to stdout. The commands are `book`, `cancel`, `get`, `list`, `help`, and `quit`.
  `BookingCli demo` runs a built-in scripted scenario. One `BookingService` instance lives for
  the whole process. It runs with `java -cp target/classes com.example.booking.cli.BookingCli`
  after `mvn package`, so no exec plugin is needed.
- **Rationale**: state survives across commands within a run (the reason for rejecting
  one-process-per-command in the clarifications), input is scriptable via a pipe, and there are
  no extra plugins (Principle III).
- **Alternatives considered**: `exec-maven-plugin` (an extra plugin just for convenience);
  argument-per-invocation (loses in-memory state).

## R9. Concurrency test design (SC-003)

- **Decision**: `@RepeatedTest(100)`. Each repetition makes 16 tasks wait on a shared
  `CountDownLatch` start gate, then all call `book("provider-1", 09:00Z, 10:00Z)`. The test
  asserts exactly 1 Booked, 15 Conflict, and that the invariant helper finds no overlapping active
  appointments. Companion tests cover 16 concurrent cancels of one id (exactly 1 Cancelled,
  15 AlreadyCancelled) and concurrent same-slot bookings across 16 different providers (all
  Booked).
- **Rationale**: the start gate maximises contention on the check-and-insert window, and 100
  repetitions satisfy SC-003. The invariant helper checks the end state independently of the
  counted results.
- **Alternatives considered**: jcstress (more rigorous, but a heavy extra dependency, which goes
  against Principle III); `Thread.sleep` injection (flaky and invasive).
