## Prompt
/speckit-specify — with the following description (verbatim):

Objective

Build a conflict-safe appointment booking and cancellation service slice. It's for a scheduling service that must never let a provider be double-booked. This is a bounded engineering exercise: a reliable, well-tested service slice, not a full web application. The work must show clear task decomposition, review of AI output, and independent verification.

Scope boundaries

In scope:
- No starter project was supplied, so build a small service or CLI prototype with an in-memory store.
- Booking and cancellation of appointments, with a provider-level overlap invariant.
- All timestamps in UTC. Document this and any other assumptions.
- A design spec, setup/run instructions, a short assumptions/invariant note, and a limitations/follow-up-risks summary.
- An AI prompt and use record, kept in prompts/.

Out of scope:
- Frontend, authentication, notifications, recurring availability, calendar integration, deployment, and any real database.
- Separate greenfield, brownfield, or ambiguous scenarios.
- Any time-zone handling beyond UTC.
- Credentials, secrets, or sensitive data in code, prompts, or records.

Acceptance criteria
- A valid appointment can be booked for a provider and a positive-duration time interval.
- A booking is rejected if it overlaps another active appointment for the same provider. Appointments for different providers never conflict.
- Adjacent appointments are allowed when one ends at the exact instant the next begins (half-open intervals).
- A cancelled appointment no longer blocks its time slot. Cancelling a missing or already-cancelled booking returns a clear, distinct result.
- Invalid timestamps, non-positive durations, and missing identifiers are rejected without changing stored bookings.
- Automated tests cover successful booking, same-provider overlap, adjacency, different providers, cancellation, and invalid input. Add a focused concurrency test for the booking check-and-insert if the chosen design allows concurrent calls.
- AI is used for at least one engineering activity. For each use, record the prompt, a response excerpt or summary, whether it was accepted, edited, or rejected, the files or decisions it informed, and how it was independently verified.
- Deliverables exist and are consistent with each other: runnable code, tests, design spec (service boundary, data/API design, booking invariant, concurrency approach, tradeoffs), setup and test commands, assumptions note, limitations summary.

Please run /speckit-specify with this. Mark anything unclear with [NEEDS CLARIFICATION] so we can resolve it with /speckit-clarify before planning. Leave the language and framework choice to the plan phase.

## Timestamp
2026-10-22T17:53:54Z

## Output artifact
- specs/001-appointment-booking/spec.md
- specs/001-appointment-booking/checklists/requirements.md
- .specify/feature.json

## Response summary
Spec with 4 prioritised user stories (book/no double-booking P1, cancel P2, concurrency P2, invalid input P3),
FR-001..FR-018, 7 success criteria, edge cases, assumptions. 3 [NEEDS CLARIFICATION] markers:
client identifier (FR-001), non-UTC timestamp handling (FR-005), interface form (FR-013).

## Human review note
Accepted by the human reviewer on 2026-10-23. The user stated they had gone through all planning artifacts ("plans you can mark as accepted by human as i have gone through all"). Edits made during review: the client-ID question was challenged and dropped (Q1 → A); Q2 and Q3 were answered by the user. 

## Verification
Human review of the full artifact(s) listed above, as stated by the user on 2026-10-23.

---

## Clarification round (same session)

### Prompt (human answers to the 3 [NEEDS CLARIFICATION] questions)
- Q1 (client identifier required?): "have i mentioned about client id? explain the question again", then after re-explanation: "Q1. A" (no client ID; provider ID only)
- Q2 (non-UTC / zoneless timestamps): "A... Keep time-zone handling to UTC and document that assumption." (reject anything not explicitly UTC)
- Q3 (interface form): "A" (in-process library + thin CLI/demo wrapper)

### Timestamp
2026-10-22

### Output artifact
- specs/001-appointment-booking/spec.md (Clarifications section; FR-001, FR-005, FR-013, User Story 3 scenario 5, User Story 4, Assumptions)
- specs/001-appointment-booking/checklists/requirements.md (all items pass)

### Human review note
Human challenged Q1 because the AI-proposed client ID was not in the brief. The AI confirmed it was not, and option A kept the spec to the brief's scope.
