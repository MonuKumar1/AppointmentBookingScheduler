package com.example.booking.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.booking.BookingService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Thin CLI wrapper: grammar in, one canonical result line out (contracts/cli.md). */
class BookingCliTest {

    private static BookingService sequentialService() {
        AtomicInteger seq = new AtomicInteger();
        return new BookingService(Clock.systemUTC(), () -> "appt-" + seq.incrementAndGet());
    }

    private static List<String> run(BookingService service, String... commands) throws IOException {
        StringWriter out = new StringWriter();
        BookingCli.run(service, new BufferedReader(new StringReader(String.join("\n", commands))),
                new PrintWriter(out, true));
        return out.toString().lines().toList();
    }

    @Test
    void tc060_outputLinesMatchContract() throws IOException {
        List<String> lines = run(sequentialService(),
                "book provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z",
                "book provider-1 2026-11-02T09:30:00Z 2026-11-02T10:30:00Z",
                "book provider-1 2026-11-02T12:00:00+05:30 2026-11-02T13:00:00Z",
                "cancel appt-1",
                "cancel appt-1",
                "cancel appt-999",
                "get appt-1",
                "get appt-999",
                "list provider-1");

        assertEquals(List.of(
                "BOOKED appt-1 provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z",
                "CONFLICT appt-1",
                "INVALID start: must be UTC",
                "CANCELLED appt-1",
                "ALREADY_CANCELLED appt-1",
                "NOT_FOUND appt-999",
                "APPOINTMENT appt-1 provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z CANCELLED",
                "NOT_FOUND appt-999",
                "APPOINTMENT appt-1 provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z CANCELLED",
                "COUNT 1"), lines);
    }

    @Test
    void tc061_badCommandsPrintErrorAndChangeNothing() throws IOException {
        BookingService service = sequentialService();
        List<String> lines = run(service,
                "",
                "# a comment",
                "frobnicate provider-1",
                "book provider-1 2026-11-02T09:00:00Z",
                "cancel",
                "list",
                "quit",
                "book provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z");

        assertEquals(4, lines.size(), () -> "unexpected output: " + lines);
        assertTrue(lines.stream().allMatch(l -> l.startsWith("ERROR ")), () -> "unexpected output: " + lines);
        assertEquals(List.of(), service.listByProvider("provider-1"), "commands after quit must not run");
    }

    @Test
    void tc062_demoShowsEveryResultKeyword() throws IOException {
        StringWriter out = new StringWriter();
        BookingCli.runDemo(new PrintWriter(out, true));
        String output = out.toString();
        for (String keyword : List.of("BOOKED", "CONFLICT", "INVALID", "CANCELLED", "ALREADY_CANCELLED",
                "NOT_FOUND", "APPOINTMENT", "COUNT")) {
            assertTrue(output.lines().anyMatch(l -> l.startsWith(keyword + " ")), () -> "missing " + keyword + ":\n" + output);
        }
        assertTrue(output.lines().noneMatch(l -> l.startsWith("ERROR")), output);
    }
}
