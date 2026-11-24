package com.weblab.rplace.weblab.rplace.core.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * The periodic destruction run's configuration (README, "Saklama ve imha"). An unknown
 * mode or a non-positive number stops the application at startup, naming the variable;
 * an EVENT_ENDED_AT that is not a date is logged, the dry run counts without it and apply
 * changes nothing (config_error) until it is fixed.
 */
@Component
public class RetentionSettings {

    private static final Logger log = LoggerFactory.getLogger(RetentionSettings.class);

    // A date in EVENT_ENDED_AT is the end of that day in Turkey.
    static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Istanbul");

    private final RetentionMode mode;

    private final Instant eventEndedAt;

    // EVENT_ENDED_AT was set but is not a date: apply changes nothing until it is fixed.
    private final boolean eventEndInvalid;

    private final int maxRows;

    private final int batchSize;

    public RetentionSettings(@Value("${retention.sweep.mode:dry-run}") String mode,
                             @Value("${event.ended-at:}") String eventEndedAt,
                             @Value("${retention.sweep.max-rows:50000}") int maxRows,
                             @Value("${retention.sweep.batch-size:500}") int batchSize) {
        this.mode = RetentionMode.parse(mode);
        this.eventEndedAt = parseEventEnd(eventEndedAt);
        this.eventEndInvalid = eventEndedAt != null && !eventEndedAt.isBlank() && this.eventEndedAt == null;
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

    /**
     * When the event ended (EVENT_ENDED_AT), or null. Optional: periods count from each row's own
     * time; when this is set, they count from it too if it is later.
     */
    /**
     * EVENT_ENDED_AT was set but is not a date. The application still starts; the dry run counts
     * without it, and apply changes nothing (config_error): a typo must not shorten a period.
     */
    public boolean eventEndInvalid() {
        return eventEndInvalid;
    }

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

    // "2025-05-18" (the end of that day in Turkey) or "2025-05-18T22:00:00+03:00"; anything else is
    // logged and counts as unset.
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
            log.warn("retention_sweep EVENT_ENDED_AT ignored: not a date (2025-05-18) or a time with its offset (2025-05-18T22:00:00+03:00)");
            return null;
        }
    }
}
