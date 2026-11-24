package com.weblab.rplace.weblab.rplace.core.retention;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * The periodic destruction run's configuration (README, "Saklama ve imha"). A value the
 * run cannot use stops the application at startup, naming the variable.
 */
@Component
public class RetentionSettings {

    // A date in EVENT_ENDED_AT is the end of that day in Turkey.
    static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Istanbul");

    private final RetentionMode mode;

    private final Instant eventEndedAt;

    private final int maxRows;

    private final int batchSize;

    public RetentionSettings(@Value("${retention.sweep.mode:dry-run}") String mode,
                             @Value("${event.ended-at:}") String eventEndedAt,
                             @Value("${retention.sweep.max-rows:50000}") int maxRows,
                             @Value("${retention.sweep.batch-size:500}") int batchSize) {
        this.mode = RetentionMode.parse(mode);
        this.eventEndedAt = parseEventEnd(eventEndedAt);
        if (maxRows < 1) {
            throw new IllegalStateException("RETENTION_SWEEP_MAX_ROWS must be a positive number");
        }
        if (batchSize < 1) {
            throw new IllegalStateException("RETENTION_SWEEP_BATCH_SIZE must be a positive number");
        }
        this.maxRows = maxRows;
        this.batchSize = batchSize;
    }

    public RetentionMode mode() {
        return mode;
    }

    /** When the event ended (EVENT_ENDED_AT), or null when it is not set: then nothing is changed. */
    public Instant eventEndedAt() {
        return eventEndedAt;
    }

    /** The brake: a rule that would change more rows than this is refused. */
    public int maxRows() {
        return maxRows;
    }

    public int batchSize() {
        return batchSize;
    }

    // "2025-05-18" (the end of that day in Turkey) or "2025-05-18T22:00:00+03:00".
    static Instant parseEventEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                return LocalDate.parse(trimmed).plusDays(1).atStartOfDay(EVENT_ZONE).toInstant();
            }
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    "EVENT_ENDED_AT must be a date (2025-05-18) or a time with its offset (2025-05-18T22:00:00+03:00)");
        }
    }
}
