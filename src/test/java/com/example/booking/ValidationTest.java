package com.example.booking;

import static com.example.booking.BookingTest.P1;
import static com.example.booking.BookingTest.at;
import static com.example.booking.BookingTest.newService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** User Story 3: invalid requests are rejected and the store is left unchanged (TC-047 in @AfterEach). */
class ValidationTest {

    private static final String OTHER = "provider-x";

    private BookingService service;
    private List<Appointment> snapshot;
    private Optional<Appointment> existing;

    @BeforeEach
    void preload() {
        service = newService();
        service.book(P1, at("09:00"), at("10:00"));
        snapshot = service.listByProvider(P1);
        existing = service.findById("appt-1");
    }

    @AfterEach
    void tc047_storeIsUnchanged() {
        assertEquals(snapshot, service.listByProvider(P1));
        assertEquals(existing, service.findById("appt-1"));
        assertEquals(List.of(), service.listByProvider(OTHER));
        assertEquals(1, snapshot.size());
    }

    @ParameterizedTest(name = "TC-040 providerId [{0}]")
    @NullSource
    @ValueSource(strings = {"", "  "})
    void tc040_blankProviderIsRequired(String providerId) {
        assertInvalid("providerId", "required", service.book(providerId, at("11:00"), at("12:00")));
    }

    @Test
    void tc041_unparseableTimestamps() {
        assertInvalid("start", "invalid timestamp", service.book(OTHER, "not-a-date", at("12:00")));
        assertInvalid("end", "invalid timestamp", service.book(OTHER, at("11:00"), "not-a-date"));
    }

    @Test
    void tc042_zonelessTimestamps() {
        assertInvalid("start", "invalid timestamp", service.book(OTHER, "2026-11-02T11:00:00", at("12:00")));
        assertInvalid("end", "invalid timestamp", service.book(OTHER, at("11:00"), "2026-11-02T12:00:00"));
    }

    @Test
    void tc043_nonUtcOffsets() {
        assertInvalid("start", "must be UTC", service.book(OTHER, "2026-11-02T11:00:00+05:30", at("12:00")));
        assertInvalid("end", "must be UTC", service.book(OTHER, at("11:00"), "2026-11-02T12:00:00+05:30"));
    }

    @Test
    void tc044_nonPositiveDurations() {
        assertInvalid("end", "must be after start", service.book(OTHER, at("11:00"), at("11:00")));
        assertInvalid("end", "must be after start", service.book(OTHER, at("11:00"), at("10:00")));
    }

    @ParameterizedTest(name = "TC-045 appointmentId [{0}]")
    @NullSource
    @ValueSource(strings = {"", "  "})
    void tc045_blankCancelIdIsInvalidNotNotFound(String appointmentId) {
        CancellationResult.Invalid invalid =
                assertInstanceOf(CancellationResult.Invalid.class, service.cancel(appointmentId));
        assertEquals("appointmentId", invalid.field());
        assertEquals("required", invalid.reason());
    }

    @Test
    void tc046_firstFailingFieldIsReported() {
        assertInvalid("providerId", "required", service.book(" ", "not-a-date", at("12:00")));
        assertInvalid("start", "invalid timestamp", service.book(OTHER, "not-a-date", "also-bad"));
    }

    private static void assertInvalid(String field, String reason, BookingResult result) {
        BookingResult.Invalid invalid = assertInstanceOf(BookingResult.Invalid.class, result);
        assertEquals(field, invalid.field());
        assertEquals(reason, invalid.reason());
    }
}
