package com.example.booking;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Strict parsing of ISO-8601 timestamps that carry an explicit zero UTC offset
 * ({@code Z}, {@code +00:00} or {@code -00:00}). Zoneless input and any non-zero offset are rejected,
 * never converted. {@link Instant#parse} is deliberately not used: it silently converts offsets.
 */
final class UtcTimestamps {

    static final String REQUIRED = "required";
    static final String INVALID_TIMESTAMP = "invalid timestamp";
    static final String MUST_BE_UTC = "must be UTC";

    private UtcTimestamps() {}

    static Instant parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidTimestamp(REQUIRED);
        }
        OffsetDateTime parsed;
        try {
            parsed = OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new InvalidTimestamp(INVALID_TIMESTAMP);
        }
        if (!parsed.getOffset().equals(ZoneOffset.UTC)) {
            throw new InvalidTimestamp(MUST_BE_UTC);
        }
        return parsed.toInstant();
    }

    /** Thrown by {@link #parse}; {@link #reason()} is one of the canonical validation reasons. */
    static final class InvalidTimestamp extends RuntimeException {
        private final String reason;

        InvalidTimestamp(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }
}
