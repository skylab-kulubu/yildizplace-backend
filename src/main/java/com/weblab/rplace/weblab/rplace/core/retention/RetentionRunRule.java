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
 * One rule's counts in one run (RetentionRun): what it matched and changed, nothing
 * about which rows. Written by RetentionSweep; the entity is here so that ddl-auto
 * creates the table.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "retention_run_rules")
public class RetentionRunRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "run_id", nullable = false)
    private long runId;

    @Column(name = "rule", nullable = false, length = 64)
    private String rule;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "table_name", nullable = false, length = 64)
    private String tableName;

    // delete or empty
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    // Rows at or before this (from the event's end or their own later time) were due.
    @Column(name = "cutoff")
    private Instant cutoff;

    @Column(name = "matched", nullable = false)
    private long matched;

    @Column(name = "changed", nullable = false)
    private long changed;

    // ok, dry_run, refused_large, would_refuse_large, failed
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;
}
