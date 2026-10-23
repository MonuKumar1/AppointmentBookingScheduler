# Appointment Booking Scheduler

A conflict-safe appointment booking and cancellation service slice. It is an in-memory,
thread-safe Java library with a thin command-line wrapper, and it never lets a provider be
double-booked. It was built end-to-end using an AI-Driven Development Life Cycle (AIDLC) on top of
[GitHub Spec Kit](https://github.com/github/spec-kit)'s spec-driven workflow.

## Setup

**Prerequisites**
- JDK 21 or newer (`java -version`). The build targets Java 21 via `maven.compiler.release=21`, so
  JDK 21, 23, 25, … all work.
- Maven 3.9+ (`mvn -v`).
- Network access on the first build only, to download JUnit and the Maven plugins.

**Build and test**

```bash
mvn clean verify                    # compile + full test suite (≈250 tests incl. 100× concurrency races)
mvn test -Dtest=ConcurrencyTest     # only the concurrency races
```

**Run the CLI**

```bash
mvn -q package -DskipTests
java -cp target/classes com.example.booking.cli.BookingCli demo    # scripted scenario
java -cp target/classes com.example.booking.cli.BookingCli         # read commands from stdin
```

Commands: `book <providerId> <start> <end>`, `cancel <appointmentId>`, `get <appointmentId>`,
`list <providerId>`, `help`, `quit`. Timestamps must be explicit UTC, e.g. `2026-11-02T09:00:00Z`.
More examples and expected output: [quickstart.md](specs/001-appointment-booking/quickstart.md).

## Documentation

| Document | Contents |
|---|---|
| [docs/design.md](docs/design.md) | Design spec: service boundary, data/API design, booking invariant, concurrency approach, tradeoffs |
| [docs/assumptions.md](docs/assumptions.md) | Assumptions and invariant note (UTC rule, half-open intervals, ids, state) |
| [docs/limitations.md](docs/limitations.md) | Limitations and follow-up risks |
| [docs/acceptance-report.md](docs/acceptance-report.md) | Validation against the brief's 7 acceptance criteria and 4 scope rules: verdicts plus test, black-box, and inspection evidence |
| [docs/test-plan.md](docs/test-plan.md) | Requirement and acceptance-scenario → test-case traceability matrix |
| [specs/001-appointment-booking/](specs/001-appointment-booking/) | Spec Kit artifacts: spec, plan, research, data model, contracts, quickstart, tasks |
| [prompts/](prompts/) | AI prompt and use record for every phase |

## Project structure

```text
pom.xml
src/main/java/com/example/booking/        BookingService (service boundary), Appointment, AppointmentStatus,
                                          BookingResult, CancellationResult, UtcTimestamps
src/main/java/com/example/booking/cli/    BookingCli (thin stdin/stdout wrapper + demo)
src/test/java/com/example/booking/        BookingTest, CancellationTest, ValidationTest, ConcurrencyTest,
                                          UtcTimestampsTest, InvariantAssertions (independent invariant check)
src/test/java/com/example/booking/cli/    BookingCliTest
docs/                                     design, assumptions, limitations, test plan
specs/001-appointment-booking/            spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md, tasks.md
prompts/                                  AI-use records, one per phase
.specify/memory/constitution.md           project principles governing every phase
memory.md                                 running project memory (state + decisions) for new sessions
```

## AIDLC workflow used

1. `/speckit-constitution`: establish project principles
2. `/speckit-specify` (+ clarifications): turn the brief into `spec.md`
3. `/speckit-plan`: architecture, stack, data model, contracts
4. `/speckit-tasks`: ordered, test-first task list
5. `/speckit-analyze`: cross-artifact consistency check before coding
6. `/speckit-implement`: execute tasks test-first (red → green)

## How AI Was Used

Each row links to the record of the exact prompt, a summary of the response, the human review and
disposition, and how the output was verified.

| Phase | Artifact | AI-generated | Human-reviewed / edited | Record |
|---|---|---|---|---|
| Constitution | `.specify/memory/constitution.md` | 5 principles, gates, governance | Accepted after review | [01-constitution.md](prompts/01-constitution.md) |
| Specify | `specs/001-appointment-booking/spec.md` | Stories, requirements, success criteria, 3 clarification questions | Edited: AI-invented client-ID question challenged and dropped; UTC and interface answers given by the human | [02-specify.md](prompts/02-specify.md) |
| Plan | `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md` | Architecture and design decisions | Edited: the human rejected the AI choosing the stack and picked Java 23 targeting 21, Maven, and JUnit 5 | [03-plan.md](prompts/03-plan.md) |
| Tasks | `tasks.md` | Test-first task breakdown | Edited: the "unlocked first, then lock" sequence was rejected (locks from the start) | [04-tasks.md](prompts/04-tasks.md) |
| Analyze | Spec/plan/tasks fixes | 12 findings | Human decided each finding (D1–D4) | [04b-analyze.md](prompts/04b-analyze.md) |
| Implement | `src/main/java/**` | All production code | Accepted after review; 2 AI-initiated doc edits approved | [05-implement.md](prompts/05-implement.md) |
| Test | `src/test/java/**`, `docs/test-plan.md` | All tests; red/green evidence per phase | Accepted; TC-010 deviation rejected and reworked | [06-test.md](prompts/06-test.md) |
| Acceptance | `docs/acceptance-report.md` | Validation against the 7 acceptance criteria (tests + 22 black-box CLI checks) | Accepted; human re-ran the suite and CLI commands | [07-acceptance.md](prompts/07-acceptance.md) |
