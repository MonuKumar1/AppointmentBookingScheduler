## Prompt
Part of the same `/speckit-implement` run (tasks T004, T009, T011, T013, T014, T016, T017, T019,
T021, T028, T031). Tests were written by the AI from the TC list in `tasks.md`, before the
corresponding production code.

## Timestamp
2026-10-23

## Output artifact
- `src/test/java/com/example/booking/`: `UtcTimestampsTest` (TC-001–004), `BookingTest` (TC-010–022),
  `CancellationTest` (TC-030–034), `ValidationTest` (TC-040–047), `ConcurrencyTest` (TC-050–053),
  `InvariantAssertions` (helper)
- `src/test/java/com/example/booking/cli/BookingCliTest.java` (TC-060–062)
- `docs/test-plan.md`: 37 test cases mapped to FR-001–FR-013 and every acceptance scenario
  US1-AS1…US4-AS2, all `pass`; DR-001–DR-005 marked as review-verified

## Response summary
- 247 test executions: 37 test cases, with parameterized and repeated tests expanded.
- **Concurrency**: TC-050 (16 threads book the same slot) and TC-051 (16 threads cancel the same id)
  are each `@RepeatedTest(100)`, using a `CountDownLatch` start gate on a fixed pool of 16 platform
  threads. Assertions: exactly one winner, and the invariant holds afterwards.
- **No-change-on-rejection**: checked by snapshot comparison (TC-020, TC-032, TC-033); for every
  validation case in `ValidationTest` it is checked in `@AfterEach` (TC-047).
- **Deviation from the task text**: TC-010 additionally checks the `Booked` result on a second, fresh
  service, because the shared fixture had already consumed `appt-1`. *(Rejected by the human; see below.)*

## Human review note
**Accepted** by the human reviewer on 2026-10-23, with one **rejection**: the TC-010 deviation (an extra
booking on a second, fresh service) was rejected. TC-010 was reworked to match the task text exactly.
It now asserts on the fixture's own `Booked` result (`appt-1`, `ACTIVE`, `createdAt` = fixed clock)
and on `findById("appt-1")` (change in `BookingTest.java`). After the rework, `mvn clean verify` →
247/247 passed.

## Verification
- Every test was seen to fail before its implementation (red counts in `05-implement.md`).
- Traceability was checked mechanically. Every FR-001–FR-013 and every scenario id appears in
  `docs/test-plan.md`, and every TC id there has a matching `tcNNN_` test method
  (`grep -rn tcNNN_ src/test`).
- The suite was stable across 3 consecutive clean runs (247/247 each).

Human verification (as stated by the user, 2026-10-23):
- ran the test suite;
- read the test code;
- checked the requirement/scenario → test mapping in `docs/test-plan.md`.
