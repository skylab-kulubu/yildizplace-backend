package com.weblab.rplace.weblab.rplace.core.retention;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The periodic destruction run (ADR-0062): once a day, deletes or empties what is past
 * its period under RetentionRules, and writes a receipt without personal data
 * (retention_runs, retention_run_rules). README, "Saklama ve imha".
 *
 * <ul>
 * <li>One run at a time: a session advisory lock on its own connection. A run that finds
 * it taken does nothing.</li>
 * <li>The schedule's run is due when no scheduled run of the same mode and event end
 * ended ok or partial in the last 23 hours, read from retention_runs: a restart never
 * makes a run due again; switching the mode or EVENT_ENDED_AT does.</li>
 * <li>Apply changes at most batch-size rows per statement, each in its own transaction
 * under a 30-second statement timeout, and never more rows than its count found.</li>
 * <li>The brake: a rule that would change more than RETENTION_SWEEP_MAX_ROWS rows is
 * refused (apply changes nothing for it; the dry run says would_refuse_large).</li>
 * <li>Periods slide: they count from each row's own time or the person's last activity
 * (RetentionRules); EVENT_ENDED_AT, when set, counts too if it is later.</li>
 * <li>Before the rules, every run (dry run too) records each account's last login in
 * users.last_seen_at, from its newest login token, so that the account rule still knows
 * it once login_tokens has removed the token; an account with no trace of activity at
 * all (from before this column) is counted from its first run, and so is a whitelist
 * entry (whitelisted_mails.retention_seen_at). The columns are added here, not on the
 * entities: the run is their only reader and writer.</li>
 * </ul>
 */
@Component
public class RetentionSweep {

    private static final Logger log = LoggerFactory.getLogger(RetentionSweep.class);

    // "placeret": Place's own key; Guessr has another database anyway.
    static final long LOCK_KEY = 0x706c616365726574L;

    static final Duration DUE_AFTER = Duration.ofHours(23);

    static final Duration IP_PERIOD = Duration.ofDays(90);

    public enum Trigger {
        SCHEDULE("schedule"),
        MANUAL("manual");

        private final String value;

        Trigger(String value) {
            this.value = value;
        }
    }

    public enum Outcome {
        RAN,
        NOT_DUE,
        LOCKED,
        OFF
    }

    public record RuleResult(String rule, String status, long matched, long changed) {
    }

    public record RunResult(Outcome outcome, String status, List<RuleResult> rules) {

        public RuleResult rule(String name) {
            return rules.stream().filter(r -> r.rule().equals(name)).findFirst().orElseThrow();
        }
    }

    private final DataSource dataSource;

    private final NamedParameterJdbcTemplate jdbc;

    private final TransactionTemplate transactions;

    private final Clock clock;

    private final RetentionSettings settings;

    public RetentionSweep(DataSource dataSource, NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                          Clock clock, RetentionSettings settings) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.settings = settings;
    }

    /** The schedule's run, in the configured mode, if it is due. */
    public RunResult runIfDue() {
        return run(settings, Trigger.SCHEDULE);
    }

    /** A run with these settings; the schedule's (Trigger.SCHEDULE) only if it is due. */
    public RunResult run(RetentionSettings settings, Trigger trigger) {
        if (settings.mode() == RetentionMode.OFF) {
            return new RunResult(Outcome.OFF, null, List.of());
        }
        Connection lock;
        try {
            lock = dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("retention sweep: no database connection (" + sqlState(e) + ")");
        }
        boolean locked = false;
        try {
            locked = tryLock(lock);
            if (!locked) {
                if (trigger == Trigger.MANUAL) {
                    log.info("retention_run trigger={} status=skipped_locked", trigger.value);
                }
                return new RunResult(Outcome.LOCKED, null, List.of());
            }
            if (trigger == Trigger.SCHEDULE && !isDue(settings)) {
                return new RunResult(Outcome.NOT_DUE, null, List.of());
            }
            return runLocked(settings, trigger);
        } finally {
            release(lock, locked);
        }
    }

    private RunResult runLocked(RetentionSettings settings, Trigger trigger) {
        Instant now = clock.instant();
        // Under the lock, a run still marked running belongs to a process that went away.
        jdbc.update("UPDATE retention_runs SET status = 'abandoned', finished_at = :now WHERE status = 'running'",
                new MapSqlParameterSource("now", at(now)));
        Long runId = jdbc.queryForObject("""
                        INSERT INTO retention_runs (mode, triggered_by, event_ended_at, started_at, status)
                        VALUES (:mode, :trigger, :eventEnd, :now, 'running') RETURNING id""",
                new MapSqlParameterSource()
                        .addValue("mode", settings.mode().value())
                        .addValue("trigger", trigger.value)
                        .addValue("eventEnd", at(settings.eventEndedAt()))
                        .addValue("now", at(now)),
                Long.class);

        List<RuleResult> results = new ArrayList<>();
        String status = "ok";
        String errorCode = null;
        try {
            recordLastLogins(now);
            MapSqlParameterSource params = parameters(settings.eventEndedAt(), now);
            for (RetentionRule rule : RetentionRules.ALL) {
                RuleResult result = runRule(rule, settings, params, runId);
                results.add(result);
                if (result.status().equals("failed") || result.status().equals("refused_large")) {
                    status = "partial";
                }
            }
        } catch (RuntimeException e) {
            status = "failed";
            errorCode = errorCode(e);
        }
        jdbc.update("UPDATE retention_runs SET status = :status, finished_at = :now, error_code = :code WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("now", at(clock.instant()))
                        .addValue("code", errorCode)
                        .addValue("id", runId));
        log.info("retention_run id={} mode={} trigger={} status={} event_ended_at={} matched={} changed={} code={}",
                runId, settings.mode().value(), trigger.value, status,
                settings.eventEndedAt() == null ? "unset" : settings.eventEndedAt(),
                results.stream().mapToLong(RuleResult::matched).sum(),
                results.stream().mapToLong(RuleResult::changed).sum(),
                errorCode == null ? "-" : errorCode);
        return new RunResult(Outcome.RAN, status, results);
    }

    private RuleResult runRule(RetentionRule rule, RetentionSettings settings, MapSqlParameterSource params, long runId) {
        Instant cutoff = ((OffsetDateTime) params.getValue(rule.cutoff().parameter())).toInstant();
        long matched = 0;
        long changed = 0;
        String status;
        String errorCode = null;
        try {
            matched = inTransaction(() -> jdbc.queryForObject(rule.countSql(), params, Long.class));
            if (matched > settings.maxRows()) {
                status = settings.mode() == RetentionMode.APPLY ? "refused_large" : "would_refuse_large";
            } else if (settings.mode() == RetentionMode.DRY_RUN) {
                status = "dry_run";
            } else {
                while (changed < matched) {
                    MapSqlParameterSource batch = new MapSqlParameterSource(params.getValues())
                            .addValue("batch", Math.min(settings.batchSize(), matched - changed));
                    int n = inTransaction(() -> jdbc.update(rule.batchSql(), batch));
                    if (n == 0) {
                        break;
                    }
                    changed += n;
                }
                status = "ok";
            }
        } catch (DataAccessException e) {
            status = "failed";
            errorCode = errorCode(e);
        }
        jdbc.update("""
                        INSERT INTO retention_run_rules (run_id, rule, version, table_name, action, cutoff, matched, changed, status, error_code)
                        VALUES (:run, :rule, :version, :table, :action, :cutoff, :matched, :changed, :status, :code)""",
                new MapSqlParameterSource()
                        .addValue("run", runId)
                        .addValue("rule", rule.name())
                        .addValue("version", rule.version())
                        .addValue("table", rule.table())
                        .addValue("action", rule.action().value())
                        .addValue("cutoff", at(cutoff))
                        .addValue("matched", matched)
                        .addValue("changed", changed)
                        .addValue("status", status)
                        .addValue("code", errorCode));
        // Counts only: no address, IP or row id.
        log.info("retention_rule rule={} version={} mode={} status={} table={} action={} cutoff={} matched={} changed={} code={}",
                rule.name(), rule.version(), settings.mode().value(), status, rule.table(), rule.action().value(),
                cutoff == null ? "-" : cutoff, matched, changed, errorCode == null ? "-" : errorCode);
        return new RuleResult(rule.name(), status, matched, changed);
    }

    private void recordLastLogins(Instant now) {
        addColumnIfMissing("users", "last_seen_at");
        addColumnIfMissing("whitelisted_mails", "retention_seen_at");
        inTransaction(() -> jdbc.update("""
                        UPDATE users u SET last_seen_at = t.at
                        FROM (SELECT user_id, max(created_at) AS at FROM user_tokens GROUP BY user_id) t
                        WHERE t.user_id = u.id AND t.at IS NOT NULL AND (u.last_seen_at IS NULL OR u.last_seen_at < t.at)""",
                new MapSqlParameterSource()));
        // No trace of activity at all (accounts from before last_seen_at): counted from now.
        inTransaction(() -> jdbc.update(
                "UPDATE users SET last_seen_at = :now WHERE last_seen_at IS NULL AND last_placed_at IS NULL",
                new MapSqlParameterSource("now", at(now))));
        // A whitelist entry has no time of its own: counted from the first run that saw it.
        inTransaction(() -> jdbc.update(
                "UPDATE whitelisted_mails SET retention_seen_at = :now WHERE retention_seen_at IS NULL",
                new MapSqlParameterSource("now", at(now))));
    }

    // Checked first, and under a short lock timeout: ALTER TABLE queues behind every open query on
    // the table, and every request behind it would wait too.
    private void addColumnIfMissing(String table, String column) {
        Integer columns = jdbc.queryForObject("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = :table AND column_name = :column""",
                new MapSqlParameterSource().addValue("table", table).addValue("column", column), Integer.class);
        if (columns != null && columns > 0) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '5s'");
            jdbc.getJdbcTemplate().execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " timestamptz");
        });
    }

    private MapSqlParameterSource parameters(Instant eventEnd, Instant now) {
        OffsetDateTime utcNow = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        return new MapSqlParameterSource()
                .addValue("eventEnd", at(eventEnd))
                .addValue("ipCutoff", utcNow.minus(IP_PERIOD))
                .addValue("accountCutoff", utcNow.minusYears(1))
                .addValue("banCutoff", utcNow.minusYears(2));
    }

    // A new EVENT_ENDED_AT makes the day's run due again too.
    private boolean isDue(RetentionSettings settings) {
        Integer recent = jdbc.queryForObject("""
                        SELECT count(*) FROM retention_runs
                        WHERE triggered_by = 'schedule' AND mode = :mode AND status IN ('ok', 'partial') AND started_at > :since
                        AND event_ended_at IS NOT DISTINCT FROM CAST(:eventEnd AS timestamptz)""",
                new MapSqlParameterSource()
                        .addValue("mode", settings.mode().value())
                        .addValue("eventEnd", at(settings.eventEndedAt()))
                        .addValue("since", at(clock.instant().minus(DUE_AFTER))),
                Integer.class);
        return recent == null || recent == 0;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> {
            jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout = '30s'");
            return work.get();
        });
    }

    private static boolean tryLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("retention sweep: lock failed (" + sqlState(e) + ")");
        }
    }

    // A session lock stays with its connection: if it cannot be released, the pool must not
    // hand that connection out again.
    private void release(Connection connection, boolean locked) {
        boolean released = !locked;
        if (locked) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                statement.setLong(1, LOCK_KEY);
                statement.execute();
                released = true;
            } catch (SQLException e) {
                log.warn("retention_sweep unlock failed code={}", sqlState(e));
            }
        }
        if (!released) {
            try {
                if (dataSource.isWrapperFor(HikariDataSource.class)) {
                    dataSource.unwrap(HikariDataSource.class).evictConnection(connection);
                    return;
                }
            } catch (SQLException ignored) {
                // Closed below.
            }
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing left to do.
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** A SQLSTATE or the exception's class: never the message, which can quote a value. */
    static String errorCode(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sqlState(sql);
            }
        }
        return e.getClass().getSimpleName();
    }

    private static String sqlState(SQLException e) {
        return "sqlstate_" + e.getSQLState();
    }
}
