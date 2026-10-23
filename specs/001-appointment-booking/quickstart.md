# Quickstart & Validation Guide

How to build, test, and run the booking slice, and how to check each user story end to end.
API details: [contracts/library-api.md](contracts/library-api.md). CLI format:
[contracts/cli.md](contracts/cli.md).

## Prerequisites

- JDK 21 or newer (`java -version`). The build targets Java 21 via `maven.compiler.release=21`.
- Maven 3.9+ (`mvn -v`).
- Network access on the first build only, to download JUnit and the Maven plugins.

## Build & test

```bash
mvn -q clean verify     # compile (release 21) + run the full JUnit suite
mvn test -Dtest=ConcurrencyTest   # only the concurrency races (100 repetitions each)
```

Expected: `BUILD SUCCESS`, 0 failures, 0 errors.

## Run the CLI

```bash
mvn -q package -DskipTests
java -cp target/classes com.example.booking.cli.BookingCli demo
```

Interactive or piped:

```bash
printf '%s\n' \
  'book provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z' \
  'book provider-1 2026-11-02T09:30:00Z 2026-11-02T10:30:00Z' \
  'book provider-1 2026-11-02T10:00:00Z 2026-11-02T11:00:00Z' \
  'book provider-2 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z' \
  'book provider-1 2026-11-02T12:00:00+05:30 2026-11-02T13:00:00Z' \
  'list provider-1' \
| java -cp target/classes com.example.booking.cli.BookingCli
```

Expected result keywords, line by line: `BOOKED`, `CONFLICT`, `BOOKED` (adjacent), `BOOKED` (other
provider), `INVALID start: must be UTC`, then two `APPOINTMENT` lines and `COUNT 2`.

## Validation scenarios → where they are proven

| Story | Scenario | Automated proof | Manual check |
|---|---|---|---|
| US1 | Book, overlap (partial / containing / contained / identical), adjacency both sides, other provider | `BookingTest` | piped example above |
| US2 | Cancel → slot freed → rebook; cancel again → `ALREADY_CANCELLED`; unknown id → `NOT_FOUND` | `CancellationTest` | `demo` mode output |
| US3 | Unparseable, zoneless, non-UTC, zero/negative duration, blank ids; store unchanged | `ValidationTest`, `UtcTimestampsTest` | `book … +05:30 …` above |
| US4 | 16-thread same-slot race ×100 → exactly 1 `Booked`; double-cancel race; multi-provider | `ConcurrencyTest` | n/a (automated only) |
| CLI | Grammar and output lines match the contract | `BookingCliTest` | `demo` mode |

Requirement-level traceability (FR → test case IDs) lives in `docs/test-plan.md`.
