# Project Memory

Running record of state and decisions, so a new session or a new machine can pick up
where the last one stopped. Update it at the end of every phase or whenever a decision is made.
Detail lives in the linked files; keep this file short.

## Current state (as of 2026-10-23)

- Active feature: `specs/001-appointment-booking/` (`.specify/feature.json` is gitignored. On a new machine, recreate it as `{"feature_directory": "specs/001-appointment-booking"}`)
- Phases done: `/speckit-constitution` (v1.0.0), `/speckit-specify` + clarifications, `/speckit-plan` (plan, research, data-model, contracts, quickstart), `/speckit-tasks`, `/speckit-analyze`, `/speckit-implement` (all 32 tasks done; 247/247 tests green). `spec.md` has 0 [NEEDS CLARIFICATION]; quality checklist passes.
- **Next step:** commit and push the review fixes (records 01–09 accepted; tooling untracked via `git rm --cached`).
- Committed by the user as `79732fa` on `main`. Acceptance validation done afterwards: all 7 brief criteria met and scope S1–S4 within bounds (`docs/acceptance-report.md`, 247 tests + 22/22 black-box CLI checks). Uncommitted: the report, `prompts/07-acceptance.md`, a README link row, and this file.

## Decisions made

| Date | Decision | Where recorded |
|---|---|---|
| 2026-10-22 | No client/customer ID; booking needs provider ID only; cancel needs appointment ID | spec.md FR-001, Clarifications |
| 2026-10-22 | Timestamps must be explicit UTC (`Z` / `+00:00` / `-00:00`, added 2026-10-23); other offsets or zoneless input are rejected, not converted | spec.md FR-005, Assumptions |
| 2026-10-22 | Interface: in-process, thread-safe library (service boundary) + thin CLI/demo wrapper; concurrency test required | spec.md FR-013, User Story 4 |
| 2026-10-22 | Constitution v1.0.0 adopted: invariant, test-first, simplicity, traceable AI use, consistent deliverables + memory | .specify/memory/constitution.md |
| 2026-10-23 | Stack chosen by user: Java, JDK 23 compiling with `maven.compiler.release=21`, Maven, JUnit 5. No runtime deps | plan.md, research.md R1 |
| 2026-10-23 | Concurrency: per-provider `synchronized` schedule in ConcurrentHashMap; cancel re-reads under lock; global lock = documented fallback | research.md R3 |
| 2026-10-23 | API takes timestamps as strings (validation inside the slice); strict UTC via ISO_OFFSET_DATE_TIME + offset==UTC | research.md R2, R5 |
| 2026-10-23 | Canonical result names: BOOKED/CONFLICT/INVALID, CANCELLED/NOT_FOUND/ALREADY_CANCELLED | data-model.md, contracts/ |
| 2026-10-23 | Task order: US1 → US2 → US4 (red race on unlocked code, then locks) → US3 → CLI → Polish. Test IDs TC-001…TC-062 | tasks.md |
| 2026-10-23 | /speckit-analyze: FR-014–018 renumbered DR-001–005 (deliverable reqs, exempt from automated-test rule); small fixes applied. D1 = (a) locks from the start (US4 tests written inside US1/US2 phases; 32 tasks); D3 = accept `-00:00` | prompts/04b-analyze.md |
| 2026-10-23 | Acceptance validation: tests + black-box CLI run only, no mutation checks (user choice); report in docs/acceptance-report.md | prompts/07-acceptance.md |
| 2026-10-23 | Deliverables review: README points reviewers to docs/design.md first (specs/ = process trail); limitations split discovered (L7–L11) vs prototype limits (L1–L6); verbatim excerpts added to AI records | prompts/08-deliverables.md |
| 2026-10-23 | Local test UI (HTML + Swagger over a JDK HttpServer) lives in gitignored `local-ui/`; not a deliverable, no README mention, pom untouched. Run instructions live inside `local-ui/` | .gitignore |
| 2026-10-23 | Code review scored 85/100. Applied: race-test sensitivity check (TC-050 and TC-051 caught both lock removals), "37 cases / 247 executions" wording, ids exact-match documented (L12), `tc052` renamed, Spec Kit tooling untracked (constitution kept). Record 09 accepted; ids stay exact-match (L12); human code-review note added to record 05 | prompts/09-review.md |
| 2026-10-23 | CLI: stdin/stdout line commands + `demo`; run via `java -cp target/classes com.example.booking.cli.BookingCli` | contracts/cli.md |

## Fixed rules from the brief

- Half-open intervals `[start, end)`: adjacency is allowed; overlap only within the same provider and only among *active* appointments.
- In-memory store only. Out of scope: frontend, auth, notifications, recurring availability, calendar integration, deployment, real DB, non-UTC zones.
- No secrets or real personal data anywhere; use synthetic IDs.

## Conventions

- Every AI prompt/response is logged in `prompts/NN-phase.md` (format in `prompts/README.md`). Each needs a prompt, response summary, accepted/edited/rejected, files informed, and verification.
- Requirement → test traceability goes in `docs/test-plan.md`.
- README "How AI Was Used" table is filled in as each phase completes.

## Open items / pending human input

- Records 01–04b were accepted by the user on 2026-10-23.
- All records 01–07 reviewed by the user (2026-10-23). 05: accepted, both AI doc edits approved. 06: accepted; TC-010 deviation rejected and reworked. 07: accepted; driver script kept out of the repo.
- Env note: Maven runs on Homebrew JDK 25; `java` on PATH is 23. Fine because of release=21.

## Working preferences (from the user)

- The user makes technology/stack decisions. Present options and ask; never pick silently (2026-10-23).
- Build/run: `mvn clean verify` (247 tests, ~7–12 s); CLI: `mvn -q package -DskipTests && java -cp target/classes com.example.booking.cli.BookingCli demo`.
