package com.example.booking;

import static com.example.booking.InvariantAssertions.assertNoOverlappingActive;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** User Story 1: book an appointment without double-booking. */
class BookingTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    static final String P1 = "provider-1";
    static final String P2 = "provider-2";

    private BookingService service;

    static BookingService newService() {
        AtomicInteger seq = new AtomicInteger();
        return new BookingService(Clock.fixed(NOW, ZoneOffset.UTC), () -> "appt-" + seq.incrementAndGet());
    }

    static String at(String hhmm) {
        return "2026-11-02T" + hhmm + ":00Z";
    }

    @BeforeEach
    void bookExisting() {
        service = newService();
        assertInstanceOf(BookingResult.Booked.class, service.book(P1, at("09:00"), at("10:00")));
    }

    @Test
    void tc010_validBookingSucceeds() {
        Appointment appt = service.findById("appt-1").orElseThrow();
        assertEquals(P1, appt.providerId());
        assertEquals(Instant.parse(at("09:00")), appt.start());
        assertEquals(Instant.parse(at("10:00")), appt.end());
        assertEquals(AppointmentStatus.ACTIVE, appt.status());
        assertEquals(NOW, appt.createdAt());

        BookingResult result = newService().book(P1, at("09:00"), at("10:00"));
        BookingResult.Booked booked = assertInstanceOf(BookingResult.Booked.class, result);
        assertEquals("appt-1", booked.appointment().id());
        assertEquals(AppointmentStatus.ACTIVE, booked.appointment().status());
        assertNoOverlappingActive(service, P1);
    }

    @Test
    void tc011_overlapAtEndConflicts() {
        assertConflict("appt-1", service.book(P1, at("09:30"), at("10:30")));
    }

    @Test
    void tc012_overlapAtStartConflicts() {
        assertConflict("appt-1", service.book(P1, at("08:30"), at("09:30")));
    }

    @Test
    void tc013_containingIntervalConflicts() {
        assertConflict("appt-1", service.book(P1, at("08:00"), at("11:00")));
    }

    @Test
    void tc014_containedIntervalConflicts() {
        assertConflict("appt-1", service.book(P1, at("09:15"), at("09:45")));
    }

    @Test
    void tc015_identicalIntervalConflicts() {
        assertConflict("appt-1", service.book(P1, at("09:00"), at("10:00")));
    }

    @Test
    void tc016_adjacentAfterIsAllowed() {
        assertInstanceOf(BookingResult.Booked.class, service.book(P1, at("10:00"), at("11:00")));
        assertNoOverlappingActive(service, P1);
    }

    @Test
    void tc017_adjacentBeforeIsAllowed() {
        assertInstanceOf(BookingResult.Booked.class, service.book(P1, at("08:00"), at("09:00")));
        assertNoOverlappingActive(service, P1);
    }

    @Test
    void tc018_differentProviderNeverConflicts() {
        assertInstanceOf(BookingResult.Booked.class, service.book(P2, at("09:00"), at("10:00")));
        assertNoOverlappingActive(service, P1, P2);
    }

    @Test
    void tc019_reportsEarliestStartingConflict() {
        assertInstanceOf(BookingResult.Booked.class, service.book(P1, at("11:00"), at("12:00")));
        assertConflict("appt-1", service.book(P1, at("08:00"), at("13:00")));
    }

    @Test
    void tc020_conflictLeavesStoreUnchanged() {
        List<Appointment> before = service.listByProvider(P1);
        service.book(P1, at("09:30"), at("10:30"));
        assertEquals(before, service.listByProvider(P1));
        assertTrue(service.findById("appt-2").isEmpty());
    }

    @Test
    void tc021_listIsSortedAndEmptyForUnknownProvider() {
        service.book(P1, at("12:00"), at("13:00"));
        service.book(P1, at("07:00"), at("08:00"));
        List<Instant> starts = service.listByProvider(P1).stream().map(Appointment::start).toList();
        assertEquals(List.of(Instant.parse(at("07:00")), Instant.parse(at("09:00")), Instant.parse(at("12:00"))), starts);
        assertEquals(List.of(), service.listByProvider("provider-unknown"));
        assertNoOverlappingActive(service, P1);
    }

    @Test
    void tc022_comparisonsUseNanosecondPrecision() {
        BookingService fresh = newService();
        fresh.book(P1, "2026-11-02T09:00:00Z", "2026-11-02T10:00:00.000000001Z");
        assertConflict("appt-1", fresh.book(P1, "2026-11-02T10:00:00Z", "2026-11-02T11:00:00Z"));

        assertInstanceOf(BookingResult.Booked.class,
                service.book(P1, "2026-11-02T10:00:00.000000000Z", "2026-11-02T11:00:00Z"));
        assertNoOverlappingActive(fresh, P1);
        assertNoOverlappingActive(service, P1);
    }

    private void assertConflict(String expectedId, BookingResult result) {
        BookingResult.Conflict conflict = assertInstanceOf(BookingResult.Conflict.class, result);
        assertEquals(expectedId, conflict.conflictingAppointmentId());
        assertNoOverlappingActive(service, P1);
    }
}
