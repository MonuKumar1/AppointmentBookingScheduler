# Acceptance Report

Validation, and scope compliance, of the committed implementation (`79732fa`, 2026-10-23; TC-010 reworked afterwards on review, still 247/247) against the 7 acceptance
criteria in the brief. Each criterion has two independent kinds of evidence:

1. **Automated tests.** `mvn clean verify` gives **0 failures, 0 errors** (BUILD SUCCESS) across
   37 test cases. JUnit counts 247 executions because parameterized cases expand and the two race
   cases repeat 100 times each. Test methods are named after their case id; see the
   [test plan](test-plan.md).
2. **A black-box run through the CLI.** One `BookingCli` process was driven over stdin/stdout, as an
   outside caller would use it, with fresh inputs (`provider-A`/`provider-B`, December dates) that do
   not appear in the unit tests. Result: **22/22 checks passed**, and the CLI exited with code 0.

## Summary

| # | Criterion | Verdict |
|---|---|---|
| AC1 | Valid booking with a positive-duration interval | **Met** |
| AC2 | Same-provider overlap rejected; different providers don't conflict | **Met** |
| AC3 | Adjacent appointments allowed | **Met** |
| AC4 | Cancelled slot is bookable again; missing or already-cancelled gives a clear result | **Met** |
| AC5 | Invalid timestamps, non-positive durations, and missing ids rejected without changing state | **Met** (with note) |
| AC6 | Tests cover all named categories (+ concurrency) | **Met** (with note) |
| AC7 | AI use recorded: prompt, response, disposition, verification | **Met** |

## Evidence

### AC1: A valid appointment can be booked

- **Tests**: `tc010_validBookingSucceeds`. The result is `Booked`, status `ACTIVE`, and the appointment
  can be retrieved by id. TC-018 and TC-031 also book successfully.
- **Black-box**:
  ```text
  book provider-A 2026-12-01T09:00:00Z 2026-12-01T10:00:00Z
  → BOOKED 694d917d-… provider-A 2026-12-01T09:00:00Z 2026-12-01T10:00:00Z
  ```

### AC2: Overlap rejected for the same provider; different providers don't conflict

- **Tests**:
  - TC-011 (overlaps end), TC-012 (overlaps start), TC-013 (contains), TC-014 (contained), TC-015
    (identical).
  - TC-019: when several appointments overlap, the earliest-starting one is reported.
  - TC-020: a conflict leaves the store unchanged.
  - TC-018 and TC-052 (16 providers booking concurrently): different providers never conflict.
- **Black-box**: with `provider-A` booked 09:00–10:00, every overlapping request gets
  `CONFLICT 694d917d-…`:
  - 09:30–10:30, 08:30–09:30, 08:00–11:00, 09:15–09:45, and 09:00–10:00 → `CONFLICT`.
  - `provider-B` 09:00–10:00 → `BOOKED`.
- **Mechanism**: the overlap scan and the insert run inside `synchronized (schedule)`
  ([BookingService.java:62](../src/main/java/com/example/booking/BookingService.java#L62)), using the
  half-open test `newStart < existing.end && existing.start < newEnd`.

### AC3: Adjacent appointments are allowed

- **Tests**: TC-016 (adjacent after) and TC-017 (adjacent before). TC-022 covers adjacency and
  overlap at nanosecond precision.
- **Black-box**: 10:00–11:00 → `BOOKED`, and 08:00–09:00 → `BOOKED`, next to the 09:00–10:00
  booking.

### AC4: A cancelled appointment frees its slot; clear results for missing or already-cancelled

- **Tests**: TC-030 (cancel → `Cancelled`), TC-031 (rebook the freed slot → `Booked`, new id), TC-032
  (cancel again → `AlreadyCancelled`, store unchanged), TC-033 (unknown id → `NotFound`, store
  unchanged), TC-034 (a cancelled appointment stays retrievable). TC-051 runs 16 concurrent cancels,
  100 times; each time exactly one gets `Cancelled`.
- **Black-box**:
  ```text
  cancel 694d917d-…                → CANCELLED 694d917d-…
  book provider-A 09:00Z–10:00Z    → BOOKED 1436e606-…          (slot freed)
  cancel 694d917d-…                → ALREADY_CANCELLED 694d917d-…
  cancel appt-does-not-exist       → NOT_FOUND appt-does-not-exist
  ```

### AC5: Invalid input rejected without changing stored bookings

- **Tests**:
  - TC-041 (unparseable), TC-042 (zoneless), TC-043 (non-UTC offset).
  - TC-044 (zero and negative duration).
  - TC-040 (null/empty/blank `providerId`), TC-045 (blank cancel id → `Invalid`, not `NotFound`).
  - TC-046 (the first failing field is reported).
  - TC-047 runs after **every** validation case and asserts that the store is identical to its
    pre-test snapshot.
  - TC-001 to TC-004 test the timestamp parser directly.
- **Black-box**:
  ```text
  start = not-a-date                 → INVALID start: invalid timestamp
  end   = not-a-date                 → INVALID end: invalid timestamp
  start = 2026-12-01T12:00:00        → INVALID start: invalid timestamp     (zoneless)
  start = 2026-12-01T12:00:00+05:30  → INVALID start: must be UTC
  end == start                       → INVALID end: must be after start
  end <  start                       → INVALID end: must be after start
  book with a missing argument       → ERROR usage: book <providerId> <start> <end>
  cancel with no id                  → ERROR usage: cancel <appointmentId>
  list provider-A before vs. after the batch → identical (COUNT 4 == COUNT 4)
  ```
- **Note**: the CLI splits on whitespace, so it cannot send a *blank* id. A missing argument there
  becomes `ERROR usage`, and the service is never called. Blank and null identifiers are validated by
  the library and proven by TC-040 and TC-045 ([limitation L8](limitations.md)).

### AC6: Test coverage of the named categories

| Category named in the brief | Test cases |
|---|---|
| Successful booking | TC-010 |
| Same-provider overlap | TC-011 – TC-015, TC-019, TC-020 |
| Adjacency | TC-016, TC-017, TC-022 |
| Different providers | TC-018, TC-052 |
| Cancellation | TC-030 – TC-034 |
| Invalid input | TC-040 – TC-047, TC-001 – TC-004 |
| Concurrency (focused) | TC-050 (16 threads, same slot, ×100), TC-051 (16 concurrent cancels, ×100), TC-052, TC-053 |

- **Note on "preserve the starter's transaction or locking pattern"**: this is not applicable,
  because no starter project was supplied. The service defines its own pattern: one lock per
  provider around check + insert, and a re-read under the lock on cancel
  ([BookingService.java:62, 87, 115](../src/main/java/com/example/booking/BookingService.java#L62)).
  The focused concurrency tests above cover it. Removing either lock makes its race test fail in
  roughly half or more of the 100 repetitions (TC-050: 89–93; TC-051: 49–51), so the tests detect
  a real race; see the sensitivity check in the [test plan](test-plan.md).
- Every test was seen to fail before its implementation existed. Red/green counts per phase are in
  [prompts/05-implement.md](../prompts/05-implement.md).

### AC7: AI use recorded

| Record | Prompt | Response summary | Disposition | Files informed | Verification | Human review |
|---|---|---|---|---|---|---|
| [01-constitution](../prompts/01-constitution.md) | ✓ | ✓ | Accepted | ✓ | ✓ | Done |
| [02-specify](../prompts/02-specify.md) | ✓ | ✓ | Edited (client-ID question dropped) | ✓ | ✓ | Done |
| [03-plan](../prompts/03-plan.md) | ✓ | ✓ | Edited (human chose the stack) | ✓ | ✓ | Done |
| [04-tasks](../prompts/04-tasks.md) | ✓ | ✓ | Edited ("unlocked first" rejected) | ✓ | ✓ | Done |
| [04b-analyze](../prompts/04b-analyze.md) | ✓ | ✓ | Decided per finding | ✓ | ✓ | Done |
| [05-implement](../prompts/05-implement.md) | ✓ | ✓ | Accepted; 2 AI doc edits approved | ✓ | ✓ | Done |
| [06-test](../prompts/06-test.md) | ✓ | ✓ | Accepted; TC-010 deviation rejected and reworked | ✓ | ✓ | Done |
| [07-acceptance](../prompts/07-acceptance.md) | ✓ | ✓ | Accepted | ✓ | ✓ | Done |

AC7 is met: AI was used across every phase, and every record states its disposition (accepted,
edited, or rejected) and how it was verified, including the human reviewer's own checks (all
reviewed 2026-10-23).

## Scope compliance

Checked against the brief's scope boundaries on the same commit (read-only inspection of the repo, 2026-10-23).

| # | Scope rule | Verdict | Evidence |
|---|---|---|---|
| S1 | Implement only the booking-conflict and cancellation slice, not the full scheduling product | **Within scope** | 7 production classes. The public API is only `book`, `cancel`, `findById`, and `listByProvider` ([BookingService.java](../src/main/java/com/example/booking/BookingService.java)), plus the CLI's `main`, `run`, and `runDemo`. There is no availability, rescheduling, provider registry, or user management. |
| S2 | No frontend, authentication, notifications, recurring availability, calendar integration, deployment, or new database | **Within scope** | `pom.xml` has only test-scoped JUnit, and production code imports nothing outside `java.*`. Storage is two in-memory `ConcurrentHashMap`s. A keyword scan of `src/main` for http, servlet, socket, jdbc, sql, auth, login, notif, email, recurr, cron, calendar, docker, deploy, and html found no real hits (two false positives: "ical" inside "critical" and "canonical"). There is no Dockerfile, HTML, `package.json`, or CI folder. These items are listed as out of scope in [spec.md](../specs/001-appointment-booking/spec.md) and [assumptions.md](assumptions.md). |
| S3 | No separate greenfield, brownfield, or ambiguous scenarios; work in the starter, otherwise use the minimal service or CLI fallback | **Within scope** | There is a single feature folder, `specs/001-appointment-booking/`, and no scenario folders. No starter project was supplied (recorded in [prompts/02-specify.md](../prompts/02-specify.md)), so the minimal fallback was used: an in-memory library plus a thin CLI. |
| S4 | Keep time-zone handling to UTC and document that assumption | **Within scope** | The only time-zone logic is the offset-equals-UTC check in [UtcTimestamps.java](../src/main/java/com/example/booking/UtcTimestamps.java#L32). No `ZoneId`, `ZonedDateTime`, or `atZone` conversion is used. Non-UTC input is rejected, never converted (black-box: `+05:30` → `INVALID start: must be UTC`). Documented in [assumptions.md](assumptions.md) ("All timestamps are UTC… Nothing is ever converted") and in the README's command help. |

## How to reproduce

```bash
mvn clean verify                                                    # 37 cases, 247 executions
mvn -q package -DskipTests
java -cp target/classes com.example.booking.cli.BookingCli demo     # scripted walk-through
java -cp target/classes com.example.booking.cli.BookingCli          # then type the commands shown above
```

The black-box driver that produced the 22 checks above (a Python script piping these commands into
one CLI process) was run from a scratch directory and is not committed.
