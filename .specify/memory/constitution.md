<!--
Sync Impact Report
- Version change: (unversioned template) → 1.0.0
- Modified principles: none (initial adoption). Placeholders replaced with:
  I. Booking Invariant Is Inviolable
  II. Test-First (NON-NEGOTIABLE)
  III. Simplicity & Bounded Scope
  IV. Traceable, Verified AI Use
  V. Consistent Deliverables & Shared Memory
- Added sections: Security & Data Constraints; Development Workflow & Quality Gates
- Removed sections: none
- Templates checked: plan-template.md ("Constitution Check" gate reads this file at runtime),
  spec-template.md, tasks-template.md (no edits required; not modified by this command)
- Follow-up TODOs: none
-->

# Appointment Booking Scheduler Constitution

## Core Principles

### I. Booking Invariant Is Inviolable

A provider MUST never hold two overlapping *active* appointments, under any input or interleaving
of calls.

- Appointments are half-open intervals `[start, end)`. Intervals that only touch are not overlaps.
- Only appointments for the same provider can conflict. Cancelled appointments never block a slot.
- The overlap check and the insert MUST be one atomic step per provider. A design that lets a
  concurrent caller slip between the check and the insert is non-compliant.
- A rejected request (validation, conflict, not found, already cancelled) MUST leave the store
  unchanged.

Rationale: preventing double-booking is the reason this slice exists. Every other quality is
secondary to it.

### II. Test-First (NON-NEGOTIABLE)

- For every task, tests MUST be written first and seen to fail before the implementation that
  makes them pass (red → green → refactor).
- Every functional requirement and acceptance scenario in `spec.md` MUST map to at least one
  automated test, recorded in `docs/test-plan.md`.
- Where the design permits concurrent calls, a dedicated concurrency test MUST exercise the
  check-and-insert path with competing callers and be run repeatedly.
- The full test suite MUST pass from a clean checkout using only the documented commands.

Rationale: correctness claims are only credible when independently verified by tests that
existed before the code.

### III. Simplicity & Bounded Scope

- Build the smallest thing that satisfies the spec: an in-memory store, a library service
  boundary, and a thin CLI. Real databases, frontends, auth, notifications, recurring availability,
  calendar integration, and deployment MUST NOT be added.
- All time is UTC. Input that is not explicitly UTC is rejected, never converted.
- Dependencies MUST be limited to the language runtime, its standard library, and a test framework,
  unless the plan justifies each addition in its Complexity Tracking table.
- New abstractions need a present, concrete need (YAGNI).

Rationale: this is a bounded engineering exercise, and every extra moving part is extra surface
to verify.

### IV. Traceable, Verified AI Use

- Every use of AI in an engineering activity MUST be logged in `prompts/` (format in
  `prompts/README.md`). Each record contains the prompt, a response excerpt or summary, the
  disposition (accepted, edited, or rejected), the files or decisions it informed, and how it was
  independently verified.
- AI output is a proposal, not an authority. A human MUST review it before it is accepted, and
  scope that the brief did not ask for MUST be challenged and removed.
- "Verified" means checked by something other than the AI that produced the output: a passing
  test, a manual trace against the spec, or human review against the brief.

Rationale: the exercise is assessed on review of AI output and independent verification, not only
on working code.

### V. Consistent Deliverables & Shared Memory

- The code, tests, design spec, assumptions note, limitations summary, and README MUST agree. Result
  categories, interval semantics, and the UTC rule MUST be named and described identically in every
  deliverable.
- Any change to behaviour MUST update every affected deliverable in the same change.
- `memory.md` MUST be updated whenever a phase completes or a decision is made, so that work can
  resume in a new session or on a new machine without lost context.

Rationale: mismatched documents are defects, and context lost between sessions leads to silently
re-made decisions.

## Security & Data Constraints

- No credentials, secrets, tokens, or real personal data in code, tests, prompts, records, or
  commit history.
- All sample and test identifiers MUST be synthetic (e.g. `provider-1`, `appt-…`).
- Prompts logged in `prompts/` MUST be checked for sensitive content before they are committed.

## Development Workflow & Quality Gates

Work follows the Spec Kit flow: constitution → specify → clarify → plan → tasks → implement →
converge. Each transition is logged in `prompts/`, and `memory.md` is updated afterwards.

Gates, each of which MUST pass before moving on:

1. **Spec gate**: `spec.md` has no `[NEEDS CLARIFICATION]` markers and its quality checklist
   passes.
2. **Plan gate**: the plan's Constitution Check passes against Principles I–V. Any deviation is
   recorded in Complexity Tracking with a justification.
3. **Task gate**: every requirement maps to at least one task and one planned test case in
   `docs/test-plan.md`.
4. **Done gate**: all tests pass (repeated runs for the concurrency test), the traceability matrix
   is complete, deliverables are cross-checked for consistency, and the AI-use records are complete.

## Governance

- This constitution overrides conflicting guidance in plans, tasks, and prompts. If an artifact
  conflicts with it, the artifact is changed, not the principle, unless the constitution is formally
  amended.
- Amendments are made by running `/speckit-constitution`. They MUST include a Sync Impact Report,
  a version bump, and a `prompts/` entry, and be reflected in `memory.md`.
- Versioning follows semantic versioning:
  - MAJOR: removal or redefinition of a principle.
  - MINOR: a new principle or section, or materially expanded guidance.
  - PATCH: wording or clarification only.
- Compliance is reviewed at every gate above and in `/speckit-analyze`. Reviewers MUST reject work
  that violates a principle without a recorded justification.

**Version**: 1.0.0 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-22
