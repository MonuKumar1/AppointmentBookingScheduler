# Contract: Command-Line Wrapper

Class `com.example.booking.cli.BookingCli`. A thin wrapper: it parses one line, calls
[`BookingService`](library-api.md), and prints one result. It contains no booking logic.

## Invocation

```text
java -cp target/classes com.example.booking.cli.BookingCli          # interactive / piped stdin
java -cp target/classes com.example.booking.cli.BookingCli demo     # built-in scripted scenario
```

In `demo` mode each scripted command is echoed as `> <command>` before its result line, and the
service uses sequential ids (`appt-1`, `appt-2`, …) so the script can refer to earlier bookings.
Otherwise ids are random UUIDs. One `BookingService` instance lives for the whole process, so state persists across commands
until exit. The exit code is `0` on `quit` or end of input.

## Input grammar (one command per line, whitespace-separated)

```text
book   <providerId> <start> <end>
cancel <appointmentId>
get    <appointmentId>
list   <providerId>
help
quit
```

Blank lines and lines starting with `#` are ignored, so script files can have comments.

## Output (one line per result, stdout)

Keywords match the canonical result names in [data-model.md](../data-model.md#results-sealed-outcomes).
Instants are printed in `Instant.toString()` form (always `Z`).

| Command / outcome | Line |
|---|---|
| book → Booked | `BOOKED <id> <providerId> <start> <end>` |
| book → Conflict | `CONFLICT <conflictingAppointmentId>` |
| book / cancel → Invalid | `INVALID <field>: <reason>` |
| cancel → Cancelled | `CANCELLED <id>` |
| cancel → NotFound | `NOT_FOUND <id>` |
| cancel → AlreadyCancelled | `ALREADY_CANCELLED <id>` |
| get → found | `APPOINTMENT <id> <providerId> <start> <end> <ACTIVE\|CANCELLED>` |
| get → unknown | `NOT_FOUND <id>` |
| list | zero or more `APPOINTMENT …` lines, then `COUNT <n>` |
| wrong argument count / unknown command | `ERROR <message>` (usage hint; no service call) |
| help | one `USAGE <command syntax>` line per command |
| quit | no output; stops reading input |

A missing token cannot be given on a whitespace-separated line, so a missing *argument* is reported
as `ERROR` (usage). Blank-identifier and timestamp validation are exercised through the library API
tests.
