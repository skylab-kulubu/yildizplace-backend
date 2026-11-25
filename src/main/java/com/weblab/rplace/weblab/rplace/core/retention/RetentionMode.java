package com.weblab.rplace.weblab.rplace.core.retention;

import java.util.Locale;

/** RETENTION_SWEEP_MODE: what the daily periodic destruction run does (ADR-0062). */
public enum RetentionMode {

    // No run, no query.
    OFF("off"),

    // The run counts, per rule, what apply would change and changes nothing. The default.
    DRY_RUN("dry-run"),

    // The run changes the rows.
    APPLY("apply");

    private final String value;

    RetentionMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** A blank value is the default, dry-run; any other unknown value stops the application. */
    public static RetentionMode parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return DRY_RUN;
        }
        for (RetentionMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalStateException("RETENTION_SWEEP_MODE must be off, dry-run or apply");
    }
}
