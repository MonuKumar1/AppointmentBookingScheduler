## Prompt
Human, with the brief attached as a PDF: "for attached assessement, review the below github respository and rate out of 100. follow the same evaluation criteria as charles shwab as this assessement needs to be submitted at charles schwab https://github.com/MonuKumar1/AppointmentBookingScheduler"

After the review, the human replied: "okay continue" (apply the fixes the AI offered: 1, 3, 4, 5).

## Timestamp
2026-10-23

## Output artifact
- `docs/test-plan.md`: test-count wording; new "Sensitivity check of the race tests" section
- `docs/acceptance-report.md`: test-count wording; sensitivity result added to the concurrency note
- `README.md`: test-count wording; note that Spec Kit tooling is not committed; this row in "How AI Was Used"
- `docs/assumptions.md`, `docs/limitations.md`: ids are matched exactly (new L12)
- `src/test/java/com/example/booking/ConcurrencyTest.java`: `tc052_differentProvidersNeverBlockEachOther` renamed `tc052_differentProvidersAllBookSameSlot`
- `.gitignore`: `.claude/` and `.specify/` ignored except `.specify/memory/constitution.md`; those files untracked (still on disk)
- `memory.md`: state and decisions updated

## Response summary
The AI said Schwab's internal rubric is not public, so it scored against the brief's own stated
priorities. Score: **85/100**. It verified that the local `main` matched `origin/main`, ran
`mvn clean verify` (pass), and read the service, the parser, the tests, and the docs.

- Strengths: correct half-open overlap check limited to active appointments; validation before any
  state change; per-provider lock with cancel re-reading under the lock; strict UTC parsing;
  invariant re-checked independently in tests; proportionate design doc; real human pushback
  recorded in records 02–04b and 06.
- Weaknesses, in order: (1) the AI wrote all code and tests, and record 05 shows no code-level human
  review; (2) the race tests had never been seen failing because of a race (D1-a); (3) committed
  tooling made the repo look heavier than the work; (4) one 8,104-line commit; (5) "247 tests"
  overstated 37 cases; (6) unnormalised ids undocumented, and `tc052`'s name overclaimed.
- Fix (2) was done as a sensitivity check (results in `docs/test-plan.md`). Fix (1) was left to the
  human, because it has to describe the human's own review.

## Response excerpts (verbatim)
> The AI wrote all the production code and all the tests, and the review notes say "accepted, no code changes requested." [...] there's no trace of you reviewing the code itself.

> Ids with surrounding spaces count as different providers (`" provider-1"` ≠ `"provider-1"`). Nothing documents this.

## Human review note
**Accepted as written** by the human reviewer on 2026-10-23: all fixes stay as applied.
- Ids: **keep exact match**. No code change; the behaviour stays as tested and is documented as L12.
- A code-level review note was added to record 05 (see "Human code review" there).

## Verification
By the AI:
- Sensitivity check in scratch copies (`git archive HEAD`), never in the repo: removing `synchronized`
  from `book` made TC-050 fail in 89, 91, and 93 of 100 repetitions over 3 runs; removing it from
  `cancel` made TC-051 fail in 49, 50, and 51 of 100. No other test failed in either case.
- After all edits: `mvn clean verify`, 247/247 executions, BUILD SUCCESS.
- `git ls-files` after untracking: 44 files, with `.specify/memory/constitution.md` still tracked and
  the tooling still present on disk.

Human verification (as stated by the user, 2026-10-23):
- re-ran the lock-removal sensitivity check locally: removed a lock, saw `ConcurrencyTest` fail, and
  restored it.
