# Feature Specification: Conflict-Safe Appointment Booking

**Feature Branch**: `001-appointment-booking`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "Build a conflict-safe appointment booking and cancellation service slice for a scheduling service that must never let a provider be double-booked. Bounded engineering exercise: a reliable, well-tested service slice with an in-memory store, not a full web application. Must demonstrate task decomposition, review of AI output, and independent verification." (full objective, scope boundaries, and acceptance criteria recorded in `prompts/02-specify.md`)

## Clarifications

### Session 2026-10-22

- Q: Must a booking carry a client/customer identifier? → A: No. A booking needs only the provider identifier; cancelling needs only the appointment identifier. "Missing identifier" refers to these two.
- Q: How should timestamps with a non-UTC offset or no zone designator be handled? → A: Reject them as invalid. Only explicit UTC (`Z` / `+00:00`) is accepted, and this is documented as an assumption.
- Q: Should `-00:00` be accepted as UTC? → A: Yes. It is a zero offset, and Java parses it as UTC; only non-zero offsets and zoneless input are rejected (analysis finding I1, 2026-10-23).
- Q: What caller-facing interface should the slice expose? → A: An in-process library (the service boundary) with a thin CLI/demo wrapper. It is thread-safe, so the concurrency test is required.

## User Scenarios & Testing *(mandatory)*

The primary actor is a **caller**: whoever invokes the booking slice on behalf of a provider's schedule (a person at a command line, a test harness, or another system). There is no end-user interface and no authentication; every caller is trusted.

### User Story 1 - Book an appointment without double-booking (Priority: P1)

A caller asks to book an appointment for a provider over a specific UTC time interval. If the interval is valid and does not overlap any active appointment the same provider already has, the booking is accepted and the caller receives an appointment identifier. If it overlaps, the booking is refused with a clear conflict result and nothing is stored.

**Why this priority**: The "never double-book a provider" invariant is the entire reason the slice exists. Without it there is nothing else worth delivering.

**Independent Test**: Start with an empty store, book one appointment, then attempt overlapping, adjacent, and other-provider bookings, checking each outcome and the final set of stored appointments.

**Acceptance Scenarios**:

1. **Given** an empty schedule, **When** a caller books provider P from 09:00Z to 10:00Z, **Then** the booking succeeds, an appointment identifier is returned, and the appointment is stored as active.
2. **Given** P has an active appointment 09:00Z–10:00Z, **When** a caller books P from 09:30Z to 10:30Z, **Then** the booking is rejected with a conflict result and the stored appointments are unchanged.
3. **Given** P has an active appointment 09:00Z–10:00Z, **When** a caller books P from 08:00Z to 11:00Z (fully containing it) or from 09:15Z to 09:45Z (fully contained), **Then** the booking is rejected with a conflict result.
4. **Given** P has an active appointment 09:00Z–10:00Z, **When** a caller books P from exactly 09:00Z to 10:00Z, **Then** the booking is rejected with a conflict result.
5. **Given** P has an active appointment 09:00Z–10:00Z, **When** a caller books P from 10:00Z to 11:00Z, or from 08:00Z to 09:00Z, **Then** the booking succeeds (adjacent intervals do not overlap).
6. **Given** P has an active appointment 09:00Z–10:00Z, **When** a caller books a different provider Q from 09:00Z to 10:00Z, **Then** the booking succeeds.

---

### User Story 2 - Cancel an appointment and free its slot (Priority: P2)

A caller cancels an existing active appointment by its identifier. The appointment is marked cancelled and no longer blocks its time slot, so a new booking for the same provider and overlapping interval can succeed. Cancelling an identifier that does not exist, or one that is already cancelled, returns a distinct, clearly named result for each case.

**Why this priority**: Cancellation is the only way to release capacity. It also tests that the overlap invariant considers only *active* appointments.

**Independent Test**: Book an appointment, cancel it, rebook the same slot, then attempt to cancel the same identifier again and to cancel an unknown identifier, checking every result.

**Acceptance Scenarios**:

1. **Given** P has an active appointment A at 09:00Z–10:00Z, **When** a caller cancels A, **Then** the result reports success and A's status becomes cancelled.
2. **Given** A was cancelled, **When** a caller books P from 09:00Z to 10:00Z, **Then** the booking succeeds.
3. **Given** A was cancelled, **When** a caller cancels A again, **Then** the result is "already cancelled", distinct from success and from "not found", and nothing in the store changes.
4. **Given** no appointment has identifier X, **When** a caller cancels X, **Then** the result is "not found", distinct from "already cancelled", and nothing in the store changes.

---

### User Story 3 - Reject invalid requests safely (Priority: P3)

A caller submits a malformed booking or cancellation request: an unparseable or non-UTC timestamp, an end at or before the start, or a missing or blank identifier. The request is rejected with a validation result that says what was wrong, and stored appointments are unchanged.

**Why this priority**: Bad input must never corrupt the schedule or slip past the overlap check. The core flows in P1 and P2 come first.

**Independent Test**: Snapshot the store, submit each kind of invalid request, and confirm that each is rejected with a validation result naming the problem and that the store still equals the snapshot.

**Acceptance Scenarios**:

1. **Given** any store state, **When** a caller books with a start or end that is not a valid timestamp, **Then** the request is rejected as invalid and the store is unchanged.
2. **Given** any store state, **When** a caller books with end equal to start (zero duration) or end before start (negative duration), **Then** the request is rejected as invalid and the store is unchanged.
3. **Given** any store state, **When** a caller books with a missing or blank provider identifier, **Then** the request is rejected as invalid and the store is unchanged.
4. **Given** any store state, **When** a caller cancels with a missing or blank appointment identifier, **Then** the request is rejected as invalid (not reported as "not found") and the store is unchanged.
5. **Given** any store state, **When** a caller books with a timestamp that has a non-UTC offset (e.g. `+05:30`) or no zone designator, **Then** the request is rejected as invalid and the store is unchanged (FR-005).

---

### User Story 4 - Concurrent booking attempts stay conflict-free (Priority: P2)

Two or more callers try at the same moment to book overlapping intervals for the same provider. Exactly one succeeds and every other attempt receives a conflict result. The invariant holds no matter how the calls interleave.

**Why this priority**: A check-then-insert race is the classic way double-booking happens in practice. The library is meant to be called from multiple threads (FR-013), so this story applies.

**Independent Test**: Fire many simultaneous overlapping booking requests for one provider and assert that exactly one succeeded and that the store holds no overlapping active appointments for that provider. Repeat enough times to make an intermittent race likely to show up.

**Acceptance Scenarios**:

1. **Given** an empty schedule, **When** N callers (N ≥ 10) simultaneously book P for 09:00Z–10:00Z, **Then** exactly one booking succeeds and N−1 receive a conflict result.
2. **Given** an empty schedule, **When** callers simultaneously book non-overlapping slots for P and identical slots for different providers, **Then** all of those bookings succeed.

---

### Edge Cases

- **Exact adjacency**: an end instant equal to another appointment's start instant is not an overlap (half-open intervals `[start, end)`).
- **Identical interval**: rebooking exactly the same interval for the same provider is a conflict.
- **Containment**: a new interval that fully contains, or sits fully inside, an existing one is a conflict.
- **Cancelled appointments**: they are ignored by the overlap check but stay retrievable with status cancelled.
- **Cancel then rebook then cancel again**: the original identifier stays "already cancelled"; the new booking has its own identifier.
- **Missing vs blank identifiers**: an empty or whitespace-only identifier counts as missing.
- **Sub-second precision**: overlap and adjacency comparisons use the full precision of the supplied instants. Adjacency means the instants are exactly equal.
- **Past timestamps**: booking an interval in the past is allowed (see Assumptions).
- **Partial failure**: a rejected request of any kind, whether validation or conflict, leaves no partial record behind.

## Requirements *(mandatory)*

### Functional Requirements

**Booking & invariant**

- **FR-001**: The system MUST let a caller book an appointment by supplying a provider identifier, a start instant, and an end instant. The provider identifier is the only identifier a booking requires; there is no client/customer identifier.
- **FR-002**: On a successful booking, the system MUST assign a unique appointment identifier, store the appointment with status active, and return the identifier with the stored appointment details.
- **FR-003**: The system MUST treat every appointment as the half-open interval `[start, end)`. Two intervals overlap if and only if `newStart < existingEnd` and `existingStart < newEnd`.
- **FR-004**: The system MUST reject a booking that overlaps any *active* appointment for the *same* provider, returning a `Conflict` result that identifies the conflicting appointment. Appointments for different providers MUST never conflict.
- **FR-005**: The system MUST accept and store all timestamps as UTC instants. A timestamp MUST carry an explicit zero-offset UTC designator (`Z`, `+00:00`, or `-00:00`). A timestamp with any other offset (e.g. `+05:30`) or with no zone designator MUST be rejected as invalid, with no conversion. This rule MUST be documented in the assumptions note.
- **FR-006**: The system MUST reject a booking whose duration is zero or negative (end ≤ start).

**Cancellation**

- **FR-007**: The system MUST let a caller cancel an appointment by its identifier, changing its status from active to cancelled. Cancelled appointments MUST NOT take part in overlap checks.
- **FR-008**: Cancelling an identifier that does not exist MUST return a `NotFound` result. Cancelling an already-cancelled appointment MUST return an `AlreadyCancelled` result. These two results MUST be distinct from each other and from `Cancelled`, and neither may change the store.

**Validation & integrity**

- **FR-009**: The system MUST reject unparseable timestamps, non-positive durations, and missing or blank identifiers with an `Invalid` result naming the offending field. `Invalid` MUST be distinct from `Conflict`, `NotFound`, and `AlreadyCancelled`.
- **FR-010**: A rejected request of any kind MUST leave stored appointments exactly as they were before the request.
- **FR-011**: The system MUST let a caller retrieve an appointment by identifier and list a provider's appointments, including status, so that stored state can be inspected and verified.

**Concurrency**

- **FR-012**: The overlap check and the insert of a new appointment MUST happen as one indivisible step for a given provider, so concurrent bookings can never produce overlapping active appointments. The plan phase MUST state whether the chosen design permits concurrent calls and, if so, how it enforces this.

**Interface & storage**

- **FR-013**: The slice MUST keep all appointments in an in-memory store. Data does not need to survive a restart. The slice MUST be an in-process library whose booking service is the service boundary, plus a thin CLI/demo wrapper that runs a book/cancel/inspect sequence against one store instance. The library MUST be safe to call from multiple threads at once, so FR-012 applies and the concurrency test in DR-001 is required.

### Deliverable Requirements

These describe what the repository must contain, not how the system behaves. They are verified by document and record review (tasks T025–T033), not by automated tests. Constitution Principle II's "every functional requirement maps to an automated test" applies to FR-001–FR-013. DR-001–DR-005 were formerly numbered DR-001–DR-005 (renumbered 2026-10-23, analysis finding C2).

- **DR-001**: Automated tests MUST cover successful booking, same-provider overlap (partial, containment, identical), adjacency on both sides, different providers, cancellation (success, not found, already cancelled, rebook after cancel), and each class of invalid input. They MUST also include a focused concurrency test if FR-012 permits concurrent calls.
- **DR-002**: The repository MUST contain a design spec covering service boundary, data/API design, booking invariant, concurrency approach, and tradeoffs. It MUST also contain setup, run, and test commands; a short assumptions/invariant note (including the UTC rule); and a limitations and follow-up risks summary.
- **DR-003**: Every use of AI in an engineering activity MUST be recorded in `prompts/`. Each record states the prompt, a response excerpt or summary, whether the output was accepted, edited, or rejected, the files or decisions it informed, and how it was independently verified. At least one such record MUST exist.
- **DR-004**: All deliverables MUST agree with each other. The error/result categories, interval semantics, and UTC rule described in the docs MUST match the code and tests.
- **DR-005**: No credentials, secrets, or sensitive personal data may appear in code, tests, prompts, or records. Sample identifiers MUST be synthetic.

### Key Entities

- **Provider**: whoever's time is booked. In this slice it is represented only by an opaque, non-blank identifier; no provider registry or availability calendar exists.
- **Appointment**: a reservation of a provider's time. Attributes: unique appointment identifier, provider identifier, start instant (UTC), end instant (UTC, after start), status (active | cancelled), and creation time. Cancelled appointments are kept, not deleted.
- **Booking/cancellation result**: the outcome of a request. Exactly one of: for booking, `Booked` (with appointment), `Conflict` (with the conflicting appointment's identifier), or `Invalid` (with field and reason); for cancellation, `Cancelled` (with appointment), `NotFound`, `AlreadyCancelled`, or `Invalid`. These names are canonical across code, CLI, tests, and docs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across the whole automated test suite, including repeated concurrent runs, there are zero cases of a provider holding two overlapping active appointments.
- **SC-002**: 100% of the acceptance scenarios in User Stories 1–3 (and User Story 4, if concurrent calls are permitted) have at least one passing automated test, traceable in `docs/test-plan.md`.
- **SC-003**: The concurrency test, if applicable, passes in 100 of 100 consecutive repetitions with at least 10 simultaneous competing callers per repetition.
- **SC-004**: Every rejected request in the tests (validation, conflict, not found, already cancelled) leaves the store identical to its state before the request.
- **SC-005**: A reviewer new to the repository can install, run, and execute the full test suite using only the documented commands in under 10 minutes.
- **SC-006**: Every result category produced by the code appears, with the same name and meaning, in the design spec and the tests (zero documentation/code mismatches on review).
- **SC-007**: At least one AI-use record exists in `prompts/`, and every record contains all five required elements (prompt, response summary, disposition, informed files/decisions, verification method).

## Assumptions

- All timestamps are UTC instants with an explicit `Z`/`+00:00`/`-00:00` designator (all zero offsets). Any other offset, or a missing zone, is rejected rather than converted. Daylight saving, local time zones, and provider-local business hours are out of scope.
- Intervals are half-open `[start, end)`, so back-to-back appointments are allowed.
- Booking in the past is allowed. The slice applies no "now" check, which keeps behaviour deterministic and testable; this is listed as a limitation.
- There are no limits on minimum/maximum duration, slot granularity, or how far ahead a booking can be, beyond end > start.
- Providers are implicitly created by first use. Any non-blank provider identifier is accepted.
- Cancelled appointments are kept with status cancelled rather than deleted, so "already cancelled" can be told apart from "not found".
- Cancellation needs only the appointment identifier. There is no ownership or authorisation check, because authentication is out of scope.
- The in-memory store is single-process. Data loss on restart is acceptable and is recorded as a limitation.
- The language, framework, and interface technology are chosen in the plan phase.
- Out of scope: frontend, authentication, notifications, recurring availability, calendar integration, deployment, real databases, non-UTC time-zone handling, and separate greenfield/brownfield/ambiguous scenarios.
- The project constitution (`.specify/memory/constitution.md`) is still the unfilled template, so no project-specific principles constrained this spec.
