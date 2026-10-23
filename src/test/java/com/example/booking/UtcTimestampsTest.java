package com.example.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UtcTimestampsTest {

    @ParameterizedTest(name = "TC-001 accepts {0}")
    @CsvSource({
        "2026-11-02T09:00:00Z,                2026-11-02T09:00:00Z",
        "2026-11-02T09:00Z,                   2026-11-02T09:00:00Z",
        "2026-11-02T09:00:00+00:00,           2026-11-02T09:00:00Z",
        "2026-11-02T09:00:00-00:00,           2026-11-02T09:00:00Z",
        "2026-11-02T09:00:00.123456789Z,      2026-11-02T09:00:00.123456789Z"
    })
    void tc001_acceptsExplicitUtc(String raw, String expected) {
        assertEquals(Instant.parse(expected), UtcTimestamps.parse(raw));
    }

    @Test
    void tc002_rejectsZonelessTimestamp() {
        assertReason("invalid timestamp", "2026-11-02T09:00:00");
    }

    @ParameterizedTest(name = "TC-003 rejects {0}")
    @ValueSource(strings = {"2026-11-02T09:00:00+05:30", "2026-11-02T09:00:00-04:00"})
    void tc003_rejectsNonUtcOffset(String raw) {
        assertReason("must be UTC", raw);
    }

    @ParameterizedTest(name = "TC-004 blank [{0}] is required")
    @NullSource
    @ValueSource(strings = {"", "   "})
    void tc004_rejectsBlankAsRequired(String raw) {
        assertReason("required", raw);
    }

    @ParameterizedTest(name = "TC-004 rejects {0}")
    @ValueSource(strings = {"not-a-date", "2026-13-40T99:00Z"})
    void tc004_rejectsGarbage(String raw) {
        assertReason("invalid timestamp", raw);
    }

    private static void assertReason(String expectedReason, String raw) {
        UtcTimestamps.InvalidTimestamp e =
                assertThrows(UtcTimestamps.InvalidTimestamp.class, () -> UtcTimestamps.parse(raw));
        assertEquals(expectedReason, e.reason());
    }
}
