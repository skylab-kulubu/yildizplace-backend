package com.weblab.rplace.weblab.rplace.core.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The receipt of one periodic destruction run, without personal data: mode, trigger,
 * the event end it used, times and outcome. No code path deletes it (the regulation's
 * destruction record, kept at least three years). Written by RetentionSweep; the entity
 * is here so that ddl-auto creates the table.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "retention_runs")
public class RetentionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    // dry-run or apply
    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    // schedule or manual
    @Column(name = "triggered_by", nullable = false, length = 16)
    private String triggeredBy;

    @Column(name = "event_ended_at")
    private Instant eventEndedAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    // running, ok, partial, failed, abandoned, config_error
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    // A SQLSTATE or an exception's class name, never its message (it can quote a value).
    @Column(name = "error_code", length = 64)
    private String errorCode;
}
