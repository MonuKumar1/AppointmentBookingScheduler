---

description: "Task list for Conflict-Safe Appointment Booking"
---

# Tasks: Conflict-Safe Appointment Booking

**Input**: Design documents from `specs/001-appointment-booking/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. Spec DR-001 requires them, and Constitution Principle II (Test-First,
non-negotiable) applies: every test task runs and is **seen to fail** before the implementation
task that follows it.

**Organization**: tasks are grouped by user story. Story phases follow priority: US1 (P1) → US2 (P2)
→ US3 (P3). **US4 (P2, concurrency) has no phase of its own.** Per-provider locking is built in from
the start (decision D1-a, 2026-10-23), so a concurrency test written in a later phase would never
have a red step. Its test tasks (T014, T017) are therefore written inside the US1 and US2 phases,
before the code they exercise exists.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: the user story the task belongs to (US1–US4)
- Test-case IDs (`TC-nnn`) link each test to `docs/test-plan.md`

## Path Conventions

Single Maven module at the repo root ([plan.md](plan.md#source-code-repository-root)):
production code in `src/main/java/com/example/booking/`, tests in
`src/test/java/com/example/booking/`, deliverable docs in `docs/`.

## Conventions used by every test task

- Use synthetic ids only: `provider-1`, `provider-2`, … (Security & Data Constraints).
- Construct the service as `new BookingService(Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC), seq)`,
  where `seq` returns `appt-1`, `appt-2`, … (an `AtomicInteger`-backed `Supplier<String>`), so
  results are deterministic. Concurrency tests use the default constructor.
- Canonical result names come from [data-model.md](data-model.md#results-sealed-outcomes): `Booked`,
  `Conflict`, `Invalid`, `Cancelled`, `NotFound`, `AlreadyCancelled`. Never invent others.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: a buildable, empty Maven project.

- [X] T001 Create `pom.xml` at repo root: `groupId` `com.example`, `artifactId` `appointment-booking`, `version` `0.1.0-SNAPSHOT`, packaging `jar`. Properties: `project.build.sourceEncoding=UTF-8`, `maven.compiler.release=21`. `dependencyManagement` imports `org.junit:junit-bom:5.11.4` (type `pom`, scope `import`). Single dependency `org.junit.jupiter:junit-jupiter` (scope `test`). Pin plugins `maven-compiler-plugin` 3.13.0 and `maven-surefire-plugin` 3.5.2. **No runtime dependencies** (Constitution III).
- [X] T002 [P] Add `target/`, `*.iml`, `.idea/`, and `.vscode/` to `.gitignore`.
- [X] T003 Create the directories `src/main/java/com/example/booking/cli/` and `src/test/java/com/example/booking/cli/`, then run `mvn -q verify`. Expected: BUILD SUCCESS with zero tests. This proves the toolchain works with `release=21`.
- [X] T004 [P] Replace the placeholder row in `docs/test-plan.md` with one row per test-case id listed in this file (the ids are not contiguous). Columns: requirement IDs, acceptance scenario(s) (e.g. `US1-AS2`, numbered as in spec.md), summary, test class, status `pending`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: value types, strict UTC parsing, the service skeleton, and the invariant test helper.
Every story uses them.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [X] T005 [P] Create `enum AppointmentStatus { ACTIVE, CANCELLED }` in `src/main/java/com/example/booking/AppointmentStatus.java`.
- [X] T006 [P] Create `record Appointment(String id, String providerId, Instant start, Instant end, AppointmentStatus status, Instant createdAt)` in `src/main/java/com/example/booking/Appointment.java`. Add a method `Appointment cancelled()` that returns a copy with status `CANCELLED`. It is immutable; never mutate it.
- [X] T007 [P] Create the sealed interface `BookingResult` with nested records `Booked(Appointment appointment)`, `Conflict(String conflictingAppointmentId)`, and `Invalid(String field, String reason)` in `src/main/java/com/example/booking/BookingResult.java`, exactly as in [contracts/library-api.md](contracts/library-api.md#value-types).
- [X] T008 [P] Create the sealed interface `CancellationResult` with nested records `Cancelled(Appointment appointment)`, `NotFound(String appointmentId)`, `AlreadyCancelled(Appointment appointment)`, and `Invalid(String field, String reason)` in `src/main/java/com/example/booking/CancellationResult.java`.
- [X] T009 [P] Write `UtcTimestampsTest` in `src/test/java/com/example/booking/UtcTimestampsTest.java` (parameterized where useful):
  - **TC-001**: accepts `2026-11-02T09:00:00Z`, `2026-11-02T09:00Z`, `2026-11-02T09:00:00+00:00`, `2026-11-02T09:00:00-00:00`, and `2026-11-02T09:00:00.123456789Z`, each parsing to the expected `Instant`.
  - **TC-002**: zoneless `2026-11-02T09:00:00` is rejected with reason `invalid timestamp`.
  - **TC-003**: `2026-11-02T09:00:00+05:30` and `…-04:00` are rejected with reason `must be UTC`.
  - **TC-004**: `null`, `""`, and `"   "` are rejected with reason `required`; `not-a-date` and `2026-13-40T99:00Z` are rejected with reason `invalid timestamp`.

  Run it and confirm it **fails** (it won't compile yet).
- [X] T010 Implement `final class UtcTimestamps` (package-private) in `src/main/java/com/example/booking/UtcTimestamps.java`. Provide `static Instant parse(String raw)`, which throws the nested `UtcTimestamps.InvalidTimestamp extends RuntimeException` carrying `String reason()`. The rules come verbatim from [data-model.md](data-model.md#validation-rules): blank → `required`; parse with `DateTimeFormatter.ISO_OFFSET_DATE_TIME` into `OffsetDateTime`, and a failure → `invalid timestamp`; `getOffset()` not equal to `ZoneOffset.UTC` → `must be UTC`. Do **not** use `Instant.parse` (research R5). Run `UtcTimestampsTest` and confirm it passes.
- [X] T011 [P] Create the test helper `InvariantAssertions.assertNoOverlappingActive(BookingService service, String... providerIds)` in `src/test/java/com/example/booking/InvariantAssertions.java`. For each provider it takes `listByProvider`, filters `ACTIVE`, and fails if any pair satisfies `a.start < b.end && b.start < a.end`. It must be independent of the production overlap code: write the comparison inline and do not call it.
- [X] T012 Create the `BookingService` skeleton in `src/main/java/com/example/booking/BookingService.java`:
  - Fields: `ConcurrentHashMap<String, ProviderSchedule> schedules`, `ConcurrentHashMap<String, Appointment> byId`, `Clock clock`, `Supplier<String> idGenerator`.
  - Constructors: `BookingService()` uses `Clock.systemUTC()` and `() -> UUID.randomUUID().toString()`; `BookingService(Clock, Supplier<String>)`.
  - A private static nested `ProviderSchedule` holding a `LinkedHashMap<String, Appointment> appointments`.
  - Implement `findById` (`Optional.empty()` for null, blank, or unknown) and `listByProvider` (immutable list of all statuses sorted by `start` then `createdAt`; empty for null, blank, or unknown).
  - `book` and `cancel` throw `UnsupportedOperationException` for now.

  Run `mvn -q verify` and confirm it passes.

**Checkpoint**: foundation ready, and `UtcTimestampsTest` is green.

---

## Phase 3: User Story 1 - Book an appointment without double-booking (Priority: P1) 🎯 MVP

**Goal**: valid bookings succeed; same-provider overlaps are rejected; adjacency and other providers
are allowed (FR-001–FR-004, FR-011).

**Independent Test**: on an empty service, book once, then try overlapping, adjacent, and
other-provider bookings, checking each result and the final `listByProvider`.

### Tests for User Story 1 ⚠️ write first, see them fail

- [X] T013 [P] [US1] Write `BookingTest` in `src/test/java/com/example/booking/BookingTest.java`. Use the existing booking `provider-1 09:00Z–10:00Z` on `2026-11-02` unless a case says otherwise.
  - **TC-010**: a booking succeeds: `Booked` with id `appt-1`, status `ACTIVE`, `createdAt` = the fixed clock instant, and `findById("appt-1")` returns it.
  - **TC-011**: `09:30Z–10:30Z` (overlaps the end) → `Conflict("appt-1")`.
  - **TC-012**: `08:30Z–09:30Z` (overlaps the start) → `Conflict`.
  - **TC-013**: `08:00Z–11:00Z` (contains) → `Conflict`.
  - **TC-014**: `09:15Z–09:45Z` (contained) → `Conflict`.
  - **TC-015**: `09:00Z–10:00Z` (identical) → `Conflict`.
  - **TC-016**: `10:00Z–11:00Z` (adjacent after) → `Booked`.
  - **TC-017**: `08:00Z–09:00Z` (adjacent before) → `Booked`.
  - **TC-018**: `provider-2 09:00Z–10:00Z` (different provider) → `Booked`.
  - **TC-019**: with `provider-1` holding `09:00–10:00` (`appt-1`) and `11:00–12:00` (`appt-2`), booking `08:00–13:00` → `Conflict("appt-1")`, the earliest-starting conflict.
  - **TC-020**: after any `Conflict`, `listByProvider("provider-1")` equals the snapshot taken before the request.
  - **TC-021**: `listByProvider` is sorted by start and returns an empty list for `provider-unknown`.
  - **TC-022**: nanosecond precision. With an existing `09:00:00Z–10:00:00.000000001Z`, booking `10:00:00Z–11:00:00Z` → `Conflict`; with an existing `09:00:00Z–10:00:00Z`, booking `10:00:00.000000000Z–11:00:00Z` → `Booked` (spec edge case: sub-second precision).

  Every test ends with `InvariantAssertions.assertNoOverlappingActive`. Run it and confirm it fails (`UnsupportedOperationException`).
- [X] T014 [P] [US4] Write the booking-race cases of `ConcurrencyTest` in `src/test/java/com/example/booking/ConcurrencyTest.java`. Use the default constructor, an `ExecutorService` of 16 platform threads, a `CountDownLatch(1)` start gate, and futures collected with a timeout of 10 s.
  - **TC-050** (`@RepeatedTest(100)`): 16 × `book("provider-1", "2026-11-02T09:00:00Z", "2026-11-02T10:00:00Z")` → exactly 1 `Booked` and 15 `Conflict`; then `assertNoOverlappingActive`.
  - **TC-052**: 16 threads book the same slot for `provider-1`…`provider-16` → all 16 `Booked`.
  - **TC-053**: 16 threads book 16 consecutive non-overlapping hour slots for `provider-1` → all 16 `Booked`, and the invariant holds.

  Run it and confirm it fails (`UnsupportedOperationException`).

### Implementation for User Story 1

- [X] T015 [US1] Implement `book(providerId, start, end)` in `src/main/java/com/example/booking/BookingService.java`, **with per-provider locking from the start** (research R3):
  1. Parse `start` and `end` with `UtcTimestamps.parse`.
  2. Get or create the provider's schedule with `schedules.computeIfAbsent`.
  3. Inside `synchronized (schedule)`, scan its `ACTIVE` appointments with the half-open test `newStart < existing.end() && existing.start() < newEnd` (FR-003). If any match, return `Conflict` with the earliest-starting match's id (research R4).
  4. Still inside the same block, create `new Appointment(idGenerator.get(), providerId, start, end, ACTIVE, Instant.now(clock))`, put it into the schedule and `byId`, and return `Booked`.

  Also make `listByProvider` take its snapshot inside `synchronized (schedule)`. Input validation is completed in T020 (US3). Run `BookingTest` and `ConcurrencyTest` and confirm all green.

**Checkpoint**: US1 works and is already thread-safe. MVP demo is possible via tests.

---

## Phase 4: User Story 2 - Cancel an appointment and free its slot (Priority: P2)

**Goal**: cancelling marks an appointment `CANCELLED` and frees its slot. Not-found and
already-cancelled give distinct results (FR-007, FR-008).

**Independent Test**: book, cancel, rebook the same slot, cancel the original again, and cancel an
unknown id, checking every result.

**Depends on**: US1's `book` (used to create appointments in the tests).

### Tests for User Story 2 ⚠️ write first, see them fail

- [X] T016 [P] [US2] Write `CancellationTest` in `src/test/java/com/example/booking/CancellationTest.java`:
  - **TC-030**: cancelling an active `appt-1` → `Cancelled`, whose appointment has status `CANCELLED`; `findById` shows `CANCELLED`.
  - **TC-031**: after the cancel, booking `provider-1 09:00Z–10:00Z` again → `Booked` with a new id `appt-2`.
  - **TC-032**: cancelling `appt-1` again → `AlreadyCancelled` (not `NotFound`); `listByProvider` is unchanged from before the call.
  - **TC-033**: cancelling `appt-999` → `NotFound("appt-999")` (not `AlreadyCancelled`); the store is unchanged.
  - **TC-034**: the cancelled appointment is still returned by `findById` and `listByProvider`.

  Each ends with `assertNoOverlappingActive`. Run it and confirm it fails.
- [X] T017 [P] [US4] Add the cancel-race case to `src/test/java/com/example/booking/ConcurrencyTest.java`:
  - **TC-051** (`@RepeatedTest(100)`): book one appointment, then 16 × `cancel(id)` → exactly 1 `Cancelled` and 15 `AlreadyCancelled`.

  Run it and confirm it fails (`cancel` still throws).

### Implementation for User Story 2

- [X] T018 [US2] Implement `cancel(appointmentId)` in `src/main/java/com/example/booking/BookingService.java`, **with the lock from the start**:
  1. Look up `byId`: absent → `NotFound`.
  2. Get the record's provider schedule, and inside `synchronized (schedule)` **re-read** `byId.get(id)`.
  3. If its status is `CANCELLED` → `AlreadyCancelled(existing)`. Otherwise replace the record with `existing.cancelled()` in both the schedule and `byId`, and return `Cancelled(updated)`.

  Blank-id validation comes in T020. Run `CancellationTest`, `ConcurrencyTest`, and `BookingTest` and confirm all green.

**Checkpoint**: US1, US2, and US4 are complete. The booking invariant holds under concurrency (Constitution I).

---

## Phase 5: User Story 3 - Reject invalid requests safely (Priority: P3)

**Goal**: malformed input gives an `Invalid` result naming the field, and the store is unchanged
(FR-005, FR-006, FR-009, FR-010).

**Independent Test**: snapshot the store, submit each kind of invalid request, assert `Invalid`
with the expected field and reason, and assert the store equals the snapshot.

### Tests for User Story 3 ⚠️ write first, see them fail

- [X] T019 [P] [US3] Write `ValidationTest` in `src/test/java/com/example/booking/ValidationTest.java`. Pre-load one booking (`appt-1`).
  - **TC-040**: `providerId` `null`, `""`, or `"  "` → `Invalid("providerId", "required")`.
  - **TC-041**: `start`/`end` `not-a-date` → `Invalid("start"|"end", "invalid timestamp")`.
  - **TC-042**: zoneless `2026-11-02T09:00:00` → `Invalid(…, "invalid timestamp")`.
  - **TC-043**: `+05:30` → `Invalid(…, "must be UTC")`.
  - **TC-044**: `end == start` and `end < start` → `Invalid("end", "must be after start")`.
  - **TC-045**: `cancel(null | "" | "  ")` → `CancellationResult.Invalid("appointmentId", "required")`, **not** `NotFound`.
  - **TC-046**: validation order: blank provider *and* a bad start → field `providerId`; a bad start *and* a bad end → field `start`.
  - **TC-047**: after every case above, `listByProvider("provider-1")` and `findById("appt-1")` equal the pre-test snapshot, and `listByProvider` for every provider id used in an invalid request is empty.

  Run it and confirm it fails (currently exceptions are thrown and duration is not checked).

### Implementation for User Story 3

- [X] T020 [US3] Add input validation at the top of `book` and `cancel` in `src/main/java/com/example/booking/BookingService.java`, **before** `computeIfAbsent` or any lock. Use the order from [data-model.md](data-model.md#validation-rules) verbatim:
  1. `providerId` (or `appointmentId`) blank → `required`.
  2. `start` via `UtcTimestamps.parse`, mapping `InvalidTimestamp.reason()` to `Invalid("start", reason)`.
  3. `end` likewise.
  4. `!end.isAfter(start)` → `Invalid("end", "must be after start")`.

  Return the first failure. Run `ValidationTest`, then the full suite, and confirm all green.

**Checkpoint**: all four user stories are complete and independently tested.

---

## Phase 6: CLI Wrapper (cross-cutting; FR-013)

**Purpose**: the thin, runnable command-line front end defined in [contracts/cli.md](contracts/cli.md).

- [X] T021 [P] Write `BookingCliTest` in `src/test/java/com/example/booking/cli/BookingCliTest.java`. Drive `BookingCli.run(BookingService, BufferedReader, PrintWriter)` with string input.
  - **TC-060**: every output line format in the contract table: `BOOKED <id> <providerId> <start> <end>`, `CONFLICT <id>`, `INVALID <field>: <reason>`, `CANCELLED <id>`, `NOT_FOUND <id>`, `ALREADY_CANCELLED <id>`, `APPOINTMENT … <STATUS>`, `COUNT <n>`.
  - **TC-061**: an unknown command or wrong argument count prints `ERROR …` and makes no service call (store unchanged); blank and `#` lines are ignored.
  - **TC-062**: `demo` mode runs to completion, and its output contains each of `BOOKED`, `CONFLICT`, `INVALID`, `CANCELLED`, `ALREADY_CANCELLED`, `NOT_FOUND`, `COUNT`.

  Run it and confirm it fails.
- [X] T022 Implement `BookingCli` in `src/main/java/com/example/booking/cli/BookingCli.java`.
  - `main(String[] args)`: if `args[0]` is `demo`, run the built-in script; otherwise read stdin. Exit code 0.
  - `static void run(BookingService, BufferedReader, PrintWriter)`: split each line on whitespace and dispatch `book`/`cancel`/`get`/`list`/`help`/`quit`.
  - Use exhaustive `switch` pattern matching on the sealed results. Print `Instant.toString()`.
  - Put **no booking logic** in the CLI.
  - The demo script covers: book, conflicting, adjacent, other provider, `+05:30` invalid, cancel, rebook, cancel again, cancel unknown, list.

  Run `BookingCliTest` and confirm it is green.
- [X] T023 Run the piped example and `demo` from [quickstart.md](quickstart.md#run-the-cli) by hand. Confirm the output matches the "Expected" line there, and fix the quickstart or the code if they differ.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: the deliverables required by DR-002 to DR-005 and the constitution's Done gate.

- [X] T024 [P] Write `docs/design.md`: service boundary (library vs CLI), data/API design (link [contracts/](contracts/) and [data-model.md](data-model.md); summarise, do not duplicate), the booking invariant (verbatim from data-model), the concurrency approach (per-provider monitor, cancel re-read, why there are no lost updates; the library creates no threads and is safe on platform or virtual threads), and tradeoffs (global lock, `TreeMap`, optimistic CAS, `ReentrantLock` vs `synchronized` given virtual-thread pinning on JDK 21–23, strings vs `Instant` at the boundary, from research R2–R4).
- [X] T025 [P] Write `docs/assumptions.md`, the assumptions and invariant note. Include: the UTC rule ("only explicit `Z`/`+00:00`/`-00:00`; other offsets or zoneless input are rejected, never converted"), half-open intervals, past bookings allowed, no client id, providers created implicitly, cancelled appointments kept, no auth on cancel, single-process memory.
- [X] T026 [P] Write `docs/limitations.md`, covering limitations and follow-up risks: no persistence and data loss on restart; the lock is JVM-local, not distributed; the O(n) scan (`TreeMap` follow-up); unbounded memory growth and no cleanup of cancelled appointments; no authorisation on cancel; no "not in the past" check; the concurrency test is probabilistic, not a proof; the CLI cannot express blank tokens.
- [X] T027 Update `README.md`: prerequisites (JDK 21+, Maven 3.9+), build/test/run commands copied from [quickstart.md](quickstart.md), the real project layout, and links to `docs/*`. Fill in the "How AI Was Used" table with links to each `prompts/*.md`.
- [X] T028 Update `docs/test-plan.md`: set every TC row to `pass`/`fail` from the last `mvn clean verify`, add the test method names, confirm every acceptance scenario (US1-AS1 … US4-AS2) has at least one passing TC (SC-002), confirm every FR-001…FR-013 maps to at least one TC, and confirm every DR-001…DR-005 maps to a deliverable.
- [X] T029 Cross-check consistency (DR-004, SC-006): `grep` the six result names and the three `reason` strings across `src/`, `docs/`, `specs/001-appointment-booking/`, and `README.md`. Every occurrence must agree in spelling and meaning. Fix any mismatches.
- [X] T030 [P] Scan for secrets and personal data (DR-005) across `src/`, `docs/`, `prompts/`, `specs/`, and `README.md`: `grep -rniE 'password|secret|token|api[_-]?key|BEGIN .*PRIVATE'` over the repo, and check that only synthetic ids appear in tests and the demo.
- [X] T031 Run `mvn clean verify` (root `pom.xml`) 3 times in a row. All must pass, including 100/100 repetitions per concurrency test (SC-003). Record the test counts. Then check SC-005: `git clone` the repo into a fresh scratch directory, follow **only** the README commands (build, test, run `demo`), time it, and record the duration (target: under 10 minutes).
- [X] T032 Write the AI-use records `prompts/05-implement-<phase>.md` (one per phase, or batched) and `prompts/06-test.md`, each with all five sections from `prompts/README.md` (DR-003, SC-007). Confirm the records `01`–`04b` are complete. Update `memory.md`.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → US1 → US2 → US3 → **CLI (Phase 6)** → **Polish (Phase 7)**.
- Foundational blocks every story.

### User story dependencies

- **US1 (P1)**: needs only Foundational. Its phase also writes and passes the US4 booking-race tests (T014).
- **US2 (P2)**: its tests use `book` from US1 to create appointments → after US1. Its phase also writes and passes the US4 cancel-race test (T017).
- **US4 (P2)**: no separate phase; complete when T015 and T018 are green against T014 and T017.
- **US3 (P3)**: adds validation in front of `book` and `cancel`, before `computeIfAbsent` and before any lock (T020) → after US1 and US2.
- **CLI**: needs all four stories (it exposes every result type).

### Within each phase

Test task → run and see it fail → implementation task → run and see it pass. Value types (T005–T008)
come before the service skeleton (T012).

### Parallel opportunities

- Phase 1: T002 and T004 alongside T001/T003.
- Phase 2: T005, T006, T007, T008, T009, and T011 all touch different files and can run in parallel. T010 needs T009; T012 needs T005–T008.
- Phase 3: T013 and T014 (different test files). Phase 4: T016 and T017.
- Phase 7: T024, T025, T026, and T030 in parallel; T027–T029 after them.

---

## Parallel Example: Phase 2 (Foundational)

```text
Task: "T005 Create AppointmentStatus in src/main/java/com/example/booking/AppointmentStatus.java"
Task: "T006 Create Appointment record in src/main/java/com/example/booking/Appointment.java"
Task: "T007 Create BookingResult in src/main/java/com/example/booking/BookingResult.java"
Task: "T008 Create CancellationResult in src/main/java/com/example/booking/CancellationResult.java"
Task: "T009 Write UtcTimestampsTest in src/test/java/com/example/booking/UtcTimestampsTest.java"
Task: "T011 Write InvariantAssertions in src/test/java/com/example/booking/InvariantAssertions.java"
```

## Parallel Example: Phase 7 (Polish)

```text
Task: "T024 docs/design.md"
Task: "T025 docs/assumptions.md"
Task: "T026 docs/limitations.md"
Task: "T030 secret / personal-data scan"
```

---

## Requirement Coverage

| Requirement | Tasks | Test cases |
|---|---|---|
| FR-001, FR-002 | T015 | TC-010 |
| FR-003 | T015 | TC-011–TC-017, TC-022 |
| FR-004 | T015 | TC-011–TC-015, TC-018, TC-019 |
| FR-005 | T010, T020 | TC-001–TC-004, TC-042, TC-043 |
| FR-006 | T020 | TC-044 |
| FR-007 | T018 | TC-030, TC-031 |
| FR-008 | T018 | TC-032, TC-033 |
| FR-009 | T020 | TC-040–TC-046 |
| FR-010 | T015, T018, T020 | TC-020, TC-032, TC-033, TC-047, TC-061 |
| FR-011 | T012, T015 | TC-010, TC-021, TC-034 |
| FR-012 | T015, T018 | TC-050–TC-053 |
| FR-013 | T001, T022 | TC-060–TC-062 |
| DR-001 | T009, T013, T014, T016, T017, T019, T021 | all |
| DR-002 | T024–T027 | n/a (document review, T029) |
| DR-003 | T032 | n/a (record review) |
| DR-004 | T029 | n/a |
| DR-005 | T030 | n/a |

---

## Implementation Strategy

### MVP first (User Story 1)

1. Phase 1 Setup → Phase 2 Foundational.
2. Phase 3 (US1). **Stop and validate**: `mvn test -Dtest='BookingTest,ConcurrencyTest'` is green.
   The MVP is already thread-safe.

### Incremental delivery

1. MVP (US1, plus the US4 booking races) → US2 (cancel, plus the cancel race) → US3 (validation) → CLI → Polish.
2. After each phase: run the full suite, write `prompts/05-implement-<phase>.md`, and update
   `memory.md`.

---

## Notes

- [P] = different files, no dependency on incomplete tasks.
- Record every AI-assisted task in `prompts/` as you go, not only at T032.
- Commit after each phase, once the user approves committing.
- Don't add anything the plan doesn't list (Constitution III). Raise it as a spec or plan change
  instead.
