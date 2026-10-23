package com.example.booking.cli;

import com.example.booking.Appointment;
import com.example.booking.BookingResult;
import com.example.booking.BookingService;
import com.example.booking.CancellationResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin command-line wrapper around {@link BookingService} (contracts/cli.md). It parses one command per
 * line and prints one result line; it contains no booking logic. One service instance lives for the
 * whole process.
 */
public final class BookingCli {

    private static final List<String> USAGE = List.of(
            "USAGE book <providerId> <start> <end>",
            "USAGE cancel <appointmentId>",
            "USAGE get <appointmentId>",
            "USAGE list <providerId>",
            "USAGE help | quit");

    private static final List<String> DEMO_SCRIPT = List.of(
            "book provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z",
            "book provider-1 2026-11-02T09:30:00Z 2026-11-02T10:30:00Z",
            "book provider-1 2026-11-02T10:00:00Z 2026-11-02T11:00:00Z",
            "book provider-2 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z",
            "book provider-1 2026-11-02T12:00:00+05:30 2026-11-02T13:00:00Z",
            "cancel appt-1",
            "book provider-1 2026-11-02T09:00:00Z 2026-11-02T10:00:00Z",
            "cancel appt-1",
            "cancel appt-999",
            "list provider-1");

    private BookingCli() {}

    public static void main(String[] args) throws IOException {
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        if (args.length > 0 && args[0].equals("demo")) {
            runDemo(out);
        } else {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            run(new BookingService(), in, out);
        }
    }

    /**
     * Runs the built-in scenario against a service with sequential ids ({@code appt-1}, {@code appt-2}, ...)
     * so the script can refer to earlier bookings. Each command is echoed as {@code > command}.
     */
    public static void runDemo(PrintWriter out) throws IOException {
        AtomicInteger seq = new AtomicInteger();
        BookingService service = new BookingService(Clock.systemUTC(), () -> "appt-" + seq.incrementAndGet());
        for (String command : DEMO_SCRIPT) {
            out.println("> " + command);
            run(service, new BufferedReader(new StringReader(command)), out);
        }
    }

    /** Executes commands from {@code in} until end of input or {@code quit}. */
    public static void run(BookingService service, BufferedReader in, PrintWriter out) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] args = trimmed.split("\\s+");
            if (args[0].equals("quit")) {
                return;
            }
            execute(service, args, out);
        }
    }

    private static void execute(BookingService service, String[] args, PrintWriter out) {
        switch (args[0]) {
            case "book" -> {
                if (args.length != 4) {
                    out.println("ERROR usage: book <providerId> <start> <end>");
                    return;
                }
                out.println(switch (service.book(args[1], args[2], args[3])) {
                    case BookingResult.Booked b -> "BOOKED " + b.appointment().id() + " "
                            + b.appointment().providerId() + " " + b.appointment().start() + " "
                            + b.appointment().end();
                    case BookingResult.Conflict c -> "CONFLICT " + c.conflictingAppointmentId();
                    case BookingResult.Invalid i -> "INVALID " + i.field() + ": " + i.reason();
                });
            }
            case "cancel" -> {
                if (args.length != 2) {
                    out.println("ERROR usage: cancel <appointmentId>");
                    return;
                }
                out.println(switch (service.cancel(args[1])) {
                    case CancellationResult.Cancelled c -> "CANCELLED " + c.appointment().id();
                    case CancellationResult.NotFound n -> "NOT_FOUND " + n.appointmentId();
                    case CancellationResult.AlreadyCancelled a -> "ALREADY_CANCELLED " + a.appointment().id();
                    case CancellationResult.Invalid i -> "INVALID " + i.field() + ": " + i.reason();
                });
            }
            case "get" -> {
                if (args.length != 2) {
                    out.println("ERROR usage: get <appointmentId>");
                    return;
                }
                out.println(service.findById(args[1]).map(BookingCli::format).orElse("NOT_FOUND " + args[1]));
            }
            case "list" -> {
                if (args.length != 2) {
                    out.println("ERROR usage: list <providerId>");
                    return;
                }
                List<Appointment> appointments = service.listByProvider(args[1]);
                appointments.forEach(a -> out.println(format(a)));
                out.println("COUNT " + appointments.size());
            }
            case "help" -> USAGE.forEach(out::println);
            default -> out.println("ERROR unknown command: " + args[0] + " (try help)");
        }
    }

    private static String format(Appointment a) {
        return "APPOINTMENT " + a.id() + " " + a.providerId() + " " + a.start() + " " + a.end() + " " + a.status();
    }
}
