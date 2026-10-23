# Implementation Plan: Conflict-Safe Appointment Booking

**Branch**: `001-appointment-booking` (feature id; work currently happens on `main`) | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-appointment-booking/spec.md`

## Summary

Build a thread-safe, in-process Java library that books and cancels appointments for providers.
It enforces one invariant: a provider never holds two overlapping active appointments, using
half-open UTC intervals `[start, end)`. A thin command-line wrapper drives the library over
stdin/stdout for demos and manual verification.

The overlap check and the insert run as one critical section for each provider: a per-provider
monitor, with providers held in a `ConcurrentHashMap`. Different providers therefore never block
each other, and a single provider can never be double-booked by racing callers. Inputs are plain
strings at the library boundary, so the library itself rejects invalid timestamps, non-UTC
offsets, non-positive durations, and blank identifiers before any state is touched. Every result
is a typed, sealed outcome: Booked / Conflict / Invalid, and Cancelled / NotFound /
AlreadyCancelled / Invalid.

## Technical Context

**Language/Version**: Java, compiled with JDK 23 (any JDK 21+ works). Bytecode and API level target
Java 21 LTS via `maven.compiler.release=21`.

**Primary Dependencies**: none at runtime (JDK standard library only). Test scope: JUnit Jupiter 5
(via `junit-bom`).

**Storage**: in-memory only (`ConcurrentHashMap` plus per-provider maps). Nothing is persisted.

**Testing**: JUnit 5 run by Maven Surefire. `@RepeatedTest` for the concurrency races.

**Target Platform**: any OS with JDK 21+ and Maven 3.9+ (developed on macOS).

**Project Type**: library + thin CLI (single Maven module).

**Performance Goals**: none beyond "instant" at exercise scale. Correctness comes first.
Overlap checking is a linear scan of one provider's appointments.

**Constraints**: no runtime dependencies; no network, filesystem, or database at runtime; all time
in UTC; no secrets or real personal data.

**Scale/Scope**: exercise scale, roughly hundreds of appointments per provider in one JVM. One
library package plus one CLI class, about 7 production classes and 6 test classes.

No NEEDS CLARIFICATION items remain. The stack was chosen by the user on 2026-10-23, and the design
choices are recorded in [research.md](research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Pre-design | Post-design |
|---|---|---|---|
| I. Booking Invariant Is Inviolable | Half-open overlap rule; same-provider and active-only conflicts; atomic check+insert; rejected requests leave state unchanged | PASS: FR-003, FR-004, FR-010, FR-012 adopted as-is | PASS: per-provider monitor covers check, insert, and index update ([research.md](research.md) R3). Validation runs before any lock or mutation ([data-model.md](data-model.md)). |
| II. Test-First | Tests before code; every FR mapped to a test; repeated concurrency test | PASS: Maven/JUnit chosen; TDD order is enforced in tasks | PASS: test classes planned per story ([quickstart.md](quickstart.md)); `@RepeatedTest(100)` with 16 threads meets SC-003; `docs/test-plan.md` is pre-registered by task T004 and finalised by T029 |
| III. Simplicity & Bounded Scope | In-memory; library + thin CLI; UTC only; minimal dependencies; YAGNI | PASS | PASS: zero runtime dependencies, test-only JUnit, no web server, no persistence. Linear scan is chosen over an interval tree (R4). No repository/DAO layers. |
| IV. Traceable, Verified AI Use | Every AI use logged in `prompts/` with 5 elements | PASS: `prompts/03-plan.md` records this phase | PASS |
| V. Consistent Deliverables & Memory | Result names identical across code, docs, and tests; `memory.md` updated | PASS | PASS: result vocabulary is fixed in [contracts/library-api.md](contracts/library-api.md) and reused verbatim by the CLI contract and the docs |
| Security & Data | No secrets; synthetic identifiers | PASS | PASS: samples use `provider-1`, `provider-2`; IDs are random UUIDs |

**Result**: all gates pass and there are no violations. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-appointment-booking/
├── plan.md              # This file
├── research.md          # Phase 0: decisions, rationale, alternatives
├── data-model.md        # Phase 1: entities, validation, state transitions
├── quickstart.md        # Phase 1: setup, build, test, run + validation scenarios
├── contracts/
│   ├── library-api.md   # Public Java API: the service boundary
│   └── cli.md           # Command grammar and output format of the thin CLI
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 (/speckit-tasks; not created here)
```

### Source Code (repository root)

```text
pom.xml                                   # release=21, JUnit BOM (test), Surefire

src/main/java/com/example/booking/
├── Appointment.java                      # immutable record
├── AppointmentStatus.java                # ACTIVE | CANCELLED
├── BookingResult.java                    # sealed: Booked | Conflict | Invalid
├── CancellationResult.java               # sealed: Cancelled | NotFound | AlreadyCancelled | Invalid
├── UtcTimestamps.java                    # strict UTC parsing/validation
├── BookingService.java                   # public service boundary; per-provider locking
└── cli/
    └── BookingCli.java                   # stdin/stdout command loop + `demo` mode

src/test/java/com/example/booking/
├── BookingTest.java                      # US1: success, overlap variants, adjacency, other providers
├── CancellationTest.java                 # US2: cancel, rebook, not found, already cancelled
├── ValidationTest.java                   # US3: each invalid-input class, store unchanged
├── UtcTimestampsTest.java                # FR-005 parsing edge cases
├── ConcurrencyTest.java                  # US4: same-slot race, double-cancel race, multi-provider
├── InvariantAssertions.java              # test helper: no overlapping ACTIVE per provider
└── cli/
    └── BookingCliTest.java               # CLI grammar → output lines, demo runs clean

docs/
├── design.md          # design spec: service boundary, data/API design, invariant, concurrency, tradeoffs
├── assumptions.md     # assumptions + invariant note (UTC rule, half-open, past bookings allowed)
├── limitations.md     # limitations + follow-up risks
└── test-plan.md       # (exists) requirement → test traceability matrix

README.md              # (exists) gains setup / build / test / run commands
```

**Structure Decision**: a single Maven module. The library package `com.example.booking` is the
service boundary, and `com.example.booking.cli` only depends on it, never the reverse. Tests mirror
the user stories, so each story can be verified on its own. The `docs/` files are the
assessment-facing deliverables required by DR-002. They summarise and link to this plan's
artifacts rather than duplicating them.

## Complexity Tracking

No constitution violations, so nothing to justify.
