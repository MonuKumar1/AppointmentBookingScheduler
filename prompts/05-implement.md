## Prompt
/speckit-implement (no additional arguments), run on 2026-10-23 after the human accepted the
planning artifacts and decided D1 = (a) lock from the start, and D3 = accept `-00:00`.
All 32 tasks (T001–T032) were executed in one batched session, phase by phase, test-first.

## Timestamp
2026-10-23

## Output artifact
- `pom.xml` (Java release 21, JUnit BOM 5.11.4 test-only, compiler 3.13.0, surefire 3.5.2), `.gitignore`
- `src/main/java/com/example/booking/`: `Appointment`, `AppointmentStatus`, `BookingResult`,
  `CancellationResult`, `UtcTimestamps`, `BookingService`
- `src/main/java/com/example/booking/cli/BookingCli.java`
- `docs/design.md`, `docs/assumptions.md`, `docs/limitations.md`, `README.md`
- Edits outside code (decided by the AI during implementation; flagged here for review):
  - `contracts/cli.md`: documented the `help` → `USAGE …` lines, the `quit` behaviour, and that
    `demo` echoes `> command` and uses sequential ids. The implementation needed these and the
    contract had not specified them.
  - `spec.md` FR-004/FR-008/FR-009: prose result names replaced with the canonical
    `Conflict`/`NotFound`/`AlreadyCancelled`/`Invalid`/`Cancelled` (consistency check T029).

## Response summary
- **Per-provider locking from the first version** (D1-a): `book` runs the overlap scan, the
  schedule insert, and the id-index write inside `synchronized (schedule)`. `cancel` re-reads the
  record under the same lock. `listByProvider` snapshots under the lock.
- **Validation before any state access**, in data-model order: id → start → end → duration.
  `UtcTimestamps` parses with `ISO_OFFSET_DATE_TIME` and requires offset == UTC; `Instant.parse` is
  not used.
- **CLI**: exhaustive `switch` over the sealed results, with no booking logic. `demo` uses
  sequential ids so its script can cancel `appt-1`.
- Red → green per phase:

| Phase | Red step (before the code existed) | Green |
|---|---|---|
| 2 Foundational | `UtcTimestampsTest` did not compile (`cannot find symbol UtcTimestamps`) | 13/13 |
| 3 US1 + US4 booking races | 115 errors (`UnsupportedOperationException`) in BookingTest + ConcurrencyTest | 128/128 |
| 4 US2 + US4 cancel race | 105 errors (5 CancellationTest + 100 TC-051 repetitions) | 233/233 |
| 5 US3 validation | 11/11 ValidationTest cases failed (5 failures, 6 errors) | 244/244 |
| 6 CLI | BookingCliTest did not compile (`cannot find symbol BookingCli`) | 247/247 |

## Human review note
_pending: human review of the production code and of the two AI-initiated doc edits listed under
"Output artifact" (accept / edit / reject, and why)_

## Verification
By the AI, independent of the code under test:
- `InvariantAssertions` re-implements the overlap rule without calling production code, and runs
  after every booking, cancellation and concurrency test.
- The quickstart piped example and `demo` were run by hand. Output matched the expected lines in
  `quickstart.md` exactly (BOOKED, CONFLICT, BOOKED, BOOKED, `INVALID start: must be UTC`,
  2 × APPOINTMENT, `COUNT 2`).
- `mvn clean verify` ×3 → 247/247 each run. A fresh working-tree copy (no `target/`) using only the
  README commands → 247/247, demo OK, 7 s. Caveat: Maven's cache was warm, and `git clone` was not
  possible because nothing is committed yet.

Human verification: _pending_
