# Test Plan — Traceability Matrix

Spec Kit's constitution enforces test-first implementation per task, but does
not generate a requirement-to-test traceability mapping. This file closes that
gap: every functional requirement (FR-001–FR-013) and every acceptance scenario
in `specs/001-appointment-booking/spec.md` must map to at least one passing test
case below before the feature is considered done. Deliverable requirements
(DR-001–DR-005) are verified by document and record review and are listed at the end.

Acceptance scenario ids are `US<story>-AS<n>`, numbered in the order they appear in spec.md.
All test classes are under `src/test/java/com/example/booking/`. Test methods are named after their
case id (`tc010_…`, `tc050_…`), so each row can be found with `grep -rn tcNNN_ src/test`.

**Last run (2026-10-23):** `mvn clean verify` ×3 → 247 tests, 0 failures, 0 errors each run, including
100/100 repetitions of TC-050 and TC-051 (SC-003). A fresh copy using only the README commands also
passes: 7 s with a warm Maven cache. Every acceptance scenario US1-AS1…US4-AS2 has at least one
passing case (SC-002).

| Test case | Requirement(s) | Acceptance scenario(s) | Summary | Test class | Status |
|---|---|---|---|---|---|
| TC-001 | FR-005 | US3-AS5 | Accepts `Z`, `+00:00`, `-00:00`, minute precision, nanoseconds | UtcTimestampsTest | pass |
| TC-002 | FR-005 | US3-AS5 | Zoneless timestamp → `invalid timestamp` | UtcTimestampsTest | pass |
| TC-003 | FR-005 | US3-AS5 | Non-zero offset → `must be UTC` | UtcTimestampsTest | pass |
| TC-004 | FR-005, FR-009 | US3-AS1 | Blank → `required`; garbage → `invalid timestamp` | UtcTimestampsTest | pass |
| TC-010 | FR-001, FR-002, FR-011 | US1-AS1 | Valid booking → `Booked`, ACTIVE, retrievable | BookingTest | pass |
| TC-011 | FR-003, FR-004 | US1-AS2 | Overlaps end → `Conflict` | BookingTest | pass |
| TC-012 | FR-003, FR-004 | US1-AS2 | Overlaps start → `Conflict` | BookingTest | pass |
| TC-013 | FR-003, FR-004 | US1-AS3 | Containing interval → `Conflict` | BookingTest | pass |
| TC-014 | FR-003, FR-004 | US1-AS3 | Contained interval → `Conflict` | BookingTest | pass |
| TC-015 | FR-003, FR-004 | US1-AS4 | Identical interval → `Conflict` | BookingTest | pass |
| TC-016 | FR-003 | US1-AS5 | Adjacent after → `Booked` | BookingTest | pass |
| TC-017 | FR-003 | US1-AS5 | Adjacent before → `Booked` | BookingTest | pass |
| TC-018 | FR-004 | US1-AS6 | Different provider, same slot → `Booked` | BookingTest | pass |
| TC-019 | FR-004 | US1-AS3 | Multiple conflicts → earliest-starting id reported | BookingTest | pass |
| TC-020 | FR-010 | US1-AS2 | `Conflict` leaves store unchanged | BookingTest | pass |
| TC-021 | FR-011 | — | `listByProvider` sorted; empty for unknown provider | BookingTest | pass |
| TC-022 | FR-003 | US1-AS5 | Nanosecond precision for overlap and adjacency (edge case) | BookingTest | pass |
| TC-030 | FR-007 | US2-AS1 | Cancel active → `Cancelled`, status CANCELLED | CancellationTest | pass |
| TC-031 | FR-007 | US2-AS2 | Rebook freed slot → `Booked` with new id | CancellationTest | pass |
| TC-032 | FR-008, FR-010 | US2-AS3 | Cancel again → `AlreadyCancelled`, store unchanged | CancellationTest | pass |
| TC-033 | FR-008, FR-010 | US2-AS4 | Unknown id → `NotFound`, store unchanged | CancellationTest | pass |
| TC-034 | FR-011 | US2-AS1 | Cancelled appointment still retrievable and listed | CancellationTest | pass |
| TC-040 | FR-009 | US3-AS3 | Blank `providerId` → `Invalid(providerId, required)` | ValidationTest | pass |
| TC-041 | FR-009 | US3-AS1 | Unparseable start/end → `invalid timestamp` | ValidationTest | pass |
| TC-042 | FR-005 | US3-AS5 | Zoneless → `invalid timestamp` | ValidationTest | pass |
| TC-043 | FR-005 | US3-AS5 | `+05:30` → `must be UTC` | ValidationTest | pass |
| TC-044 | FR-006 | US3-AS2 | Zero/negative duration → `Invalid(end, must be after start)` | ValidationTest | pass |
| TC-045 | FR-009 | US3-AS4 | Blank cancel id → `Invalid(appointmentId, required)`, not `NotFound` | ValidationTest | pass |
| TC-046 | FR-009 | — | Validation order: first failing field reported | ValidationTest | pass |
| TC-047 | FR-010 | US3-AS1 … US3-AS5 | Every rejection leaves the store unchanged | ValidationTest | pass |
| TC-050 | FR-012 | US4-AS1 | 16-thread same-slot race ×100 → exactly 1 `Booked` | ConcurrencyTest | pass |
| TC-051 | FR-012 | — | 16-thread double-cancel race ×100 → exactly 1 `Cancelled` | ConcurrencyTest | pass |
| TC-052 | FR-012, FR-004 | US4-AS2 | 16 providers, same slot, concurrently → all `Booked` | ConcurrencyTest | pass |
| TC-053 | FR-012 | US4-AS2 | 16 non-overlapping slots, one provider, concurrently → all `Booked` | ConcurrencyTest | pass |
| TC-060 | FR-013 | — | CLI output line formats match contracts/cli.md | cli/BookingCliTest | pass |
| TC-061 | FR-010, FR-013 | — | CLI `ERROR` for bad commands; no service call; comments ignored | cli/BookingCliTest | pass |
| TC-062 | FR-013 | — | CLI `demo` runs and shows every result keyword | cli/BookingCliTest | pass |

## Deliverable requirements (review-verified)

| Requirement | Verified by | Status |
|---|---|---|
| DR-001 Automated test coverage | This matrix (all rows pass) | pass |
| DR-002 Design spec, setup/run/test commands, assumptions, limitations | `docs/design.md`, `README.md`, `docs/assumptions.md`, `docs/limitations.md` | pass |
| DR-003 AI-use records | `prompts/*.md` | pass |
| DR-004 Deliverable consistency | Task T029 cross-check | pass |
| DR-005 No secrets or personal data | Task T030 scan | pass |
