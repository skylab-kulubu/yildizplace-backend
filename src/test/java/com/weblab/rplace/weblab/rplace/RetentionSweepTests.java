package com.weblab.rplace.weblab.rplace;

import com.weblab.rplace.weblab.rplace.core.retention.RetentionRules;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionScheduler;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionSettings;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionSweep;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionSweep.Outcome;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionSweep.RunResult;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionSweep.Trigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The periodic destruction run (ADR-0062), with sliding periods: IP addresses, login and
 * Turnstile tokens go 90 days after their row was written, the account's school address a
 * year after the person's last activity, a ban two years after it was made or lifted.
 * A standing ban and an account active in the last 90 days are never touched. The canvas and what was placed stay; a pixel log keeps its (by then
 * anonymous) user. Dry run and apply select the same rows; a second run changes
 * nothing; one run at a time; the brake; the daily schedule.
 */
@SpringBootTest
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RetentionSweepTests {

	@Autowired
	private RetentionSweep sweep;

	@Autowired
	private RetentionScheduler scheduler;

	@Autowired
	private JdbcTemplate database;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private TestClock clock;

	private Instant now;

	@BeforeEach
	void emptyTables() {
		clock.reset();
		now = clock.instant();
		database.execute("""
				TRUNCATE pixel_logs, pixels, final_pixels, user_tokens, banned_users, banned_ips, moderation_audit_log,
				whitelisted_mails, authorities, users, retention_run_rules, retention_runs RESTART IDENTITY CASCADE""");
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void theSchedulerDoesNotStartWhileTheModeIsOff() {
		assertThat(scheduler.isRunning()).isFalse();
	}

	@Test
	void periodsSlideFromEachRowsOwnTimeAndThePersonsLastActivity() {
		int played = user("oyuncu.bir@std.yildiz.edu.tr", ago(400));
		int recentLogin = user("yeni.giris@std.yildiz.edu.tr", ago(400));
		int loginOnly = user("yalniz.giris@std.yildiz.edu.tr", null);
		int banned = user("yasakli.kisi@std.yildiz.edu.tr", ago(800));
		int recentPixel = user("sonra.oynayan@std.yildiz.edu.tr", ago(10));
		int pixel = pixel(3, 4, "ff0000");
		pixelLog(pixel, played, "203.0.113.7", ago(400));
		pixelLog(pixel, recentPixel, "203.0.113.8", ago(10));
		token(played, "203.0.113.7", ago(400));
		token(recentLogin, "203.0.113.9", ago(30));
		token(loginOnly, "203.0.113.10", ago(200));
		bannedUser(banned, ago(500));
		bannedIp("198.51.100.4", ago(100));
		bannedIp("198.51.100.5", ago(10));
		audit("BAN_IP", "198.51.100.4", "bot", ago(100));
		audit("BAN_IP", "198.51.100.5", "bot", ago(10));
		audit("BAN_USER", "yasakli.kisi@std.yildiz.edu.tr", "küfür", ago(500));
		int revoked = whitelisted("eski@example.com", ago(400));
		int inForce = whitelisted("gecerli@example.com", null);
		audit("REVOKE_WHITELISTED_MAIL", "eski@example.com", null, ago(400));
		database.update("INSERT INTO final_pixels (x, y, color) VALUES (3, 4, 'ff0000')");

		RunResult dryRun = sweep.run(settings("dry-run", null), Trigger.MANUAL);

		assertThat(dryRun.status()).isEqualTo("ok");
		assertThat(counts(dryRun)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"login_tokens", 2L, "pixel_log_ip", 1L, "banned_ip", 1L, "audit_ip_target", 1L,
				"banned_account", 0L, "audit_banned_target", 0L,
				"account", 1L, "whitelist_mail", 1L, "audit_whitelist_target", 1L));
		assertThat(dryRun.rules()).allSatisfy(rule -> {
			assertThat(rule.status()).isEqualTo("dry_run");
			assertThat(rule.changed()).isZero();
		});
		assertThat(database.queryForObject("SELECT count(*) FROM users WHERE school_mail IS NULL", Integer.class)).isZero();
		assertThat(database.queryForObject("SELECT count(*) FROM user_tokens", Integer.class)).isEqualTo(3);

		RunResult apply = sweep.run(settings("apply", null), Trigger.MANUAL);

		assertThat(apply.status()).isEqualTo("ok");
		// Apply changes exactly what the dry run counted.
		assertThat(apply.rules()).allSatisfy(rule -> {
			assertThat(rule.status()).isEqualTo("ok");
			assertThat(rule.changed()).isEqualTo(dryRun.rule(rule.rule()).matched());
		});
		assertThat(mailOf(played)).isNull();
		// Active in the last 90 days, logged in 200 days ago, banned, placed 10 days ago: all stay.
		assertThat(mailOf(recentLogin)).isEqualTo("yeni.giris@std.yildiz.edu.tr");
		assertThat(mailOf(loginOnly)).isEqualTo("yalniz.giris@std.yildiz.edu.tr");
		assertThat(mailOf(banned)).isEqualTo("yasakli.kisi@std.yildiz.edu.tr");
		assertThat(mailOf(recentPixel)).isEqualTo("sonra.oynayan@std.yildiz.edu.tr");
		// The game stays: the canvas, the final canvas and every pixel log with its user and color.
		assertThat(database.queryForObject("SELECT color FROM pixels WHERE id = ?", String.class, pixel)).isEqualTo("ff0000");
		assertThat(database.queryForObject("SELECT count(*) FROM final_pixels", Integer.class)).isEqualTo(1);
		assertThat(database.queryForList("SELECT user_id, new_color, placer_ip FROM pixel_logs ORDER BY id"))
				.extracting(row -> row.get("user_id"), row -> row.get("new_color"), row -> row.get("placer_ip"))
				.containsExactly(tuple(played, "ff0000", null), tuple(recentPixel, "ff0000", "203.0.113.8"));
		assertThat(database.queryForList("SELECT user_id FROM user_tokens", Integer.class)).containsExactly(recentLogin);
		assertThat(database.queryForList("SELECT ip FROM banned_ips", String.class)).containsExactly("198.51.100.5");
		assertThat(database.queryForObject("SELECT count(*) FROM banned_users", Integer.class)).isEqualTo(1);
		assertThat(database.queryForList("SELECT action, target, reason FROM moderation_audit_log ORDER BY id"))
				.extracting(row -> row.get("action"), row -> row.get("target"), row -> row.get("reason"))
				.containsExactly(
						tuple("BAN_IP", "", null),
						tuple("BAN_IP", "198.51.100.5", "bot"),
						tuple("BAN_USER", "yasakli.kisi@std.yildiz.edu.tr", "küfür"),
						tuple("REVOKE_WHITELISTED_MAIL", "", null));
		assertThat(whitelistMail(revoked)).isNull();
		assertThat(whitelistMail(inForce)).isEqualTo("gecerli@example.com");

		RunResult again = sweep.run(settings("apply", null), Trigger.MANUAL);

		assertThat(again.rules()).allSatisfy(rule -> {
			assertThat(rule.matched()).isZero();
			assertThat(rule.changed()).isZero();
		});

		// The login 200 days ago is remembered after its token is gone: the address goes a year after it.
		clock.advance(Duration.ofDays(160));
		assertThat(sweep.run(settings("apply", null), Trigger.MANUAL).rule("account").changed()).isZero();
		clock.advance(Duration.ofDays(10));
		assertThat(sweep.run(settings("apply", null), Trigger.MANUAL).rule("account").changed()).isEqualTo(1);
		assertThat(mailOf(loginOnly)).isNull();
	}

	@Test
	void aBanAndItsRecordsGoTwoYearsAfterTheBanOrItsEnd() {
		int banned = user("iki.yil@std.yildiz.edu.tr", ago(900));
		bannedUser(banned, ago(800));
		audit("BAN_USER", "iki.yil@std.yildiz.edu.tr", "spam", ago(800));
		// Banned 800 days ago, lifted 100 days ago: two years from the lifting.
		audit("BAN_USER", "kaldirildi@std.yildiz.edu.tr", "spam", ago(800));
		audit("UNBAN_USER", "kaldirildi@std.yildiz.edu.tr", null, ago(100));
		int bannedLater = user("sonra.yasak@std.yildiz.edu.tr", ago(900));
		bannedUser(bannedLater, ago(100));
		audit("BAN_USER", "sonra.yasak@std.yildiz.edu.tr", "spam", ago(100));

		RunResult apply = sweep.run(settings("apply", null), Trigger.MANUAL);

		assertThat(apply.rule("banned_account").changed()).isEqualTo(1);
		// The ban ends and, in the same run, the account's address goes (its activity is old too).
		assertThat(apply.rule("account").changed()).isEqualTo(1);
		assertThat(mailOf(banned)).isNull();
		// A standing ban keeps its account however old its activity.
		assertThat(mailOf(bannedLater)).isEqualTo("sonra.yasak@std.yildiz.edu.tr");
		assertThat(database.queryForList("SELECT banned_user_id FROM banned_users", Integer.class)).containsExactly(bannedLater);
		assertThat(database.queryForList("SELECT target FROM moderation_audit_log ORDER BY id", String.class))
				.containsExactly("", "kaldirildi@std.yildiz.edu.tr", "kaldirildi@std.yildiz.edu.tr", "sonra.yasak@std.yildiz.edu.tr");
	}

	@Test
	void anEventEndCountsTooWhenItIsLater() {
		int played = user("etkinlik@std.yildiz.edu.tr", ago(400));
		token(played, "203.0.113.11", ago(400));

		RunResult withRecentEvent = sweep.run(settings("apply", now.minus(Duration.ofDays(50))), Trigger.MANUAL);

		assertThat(withRecentEvent.rules()).allSatisfy(rule -> assertThat(rule.matched()).isZero());

		RunResult withOldEvent = sweep.run(settings("apply", now.minus(Duration.ofDays(100))), Trigger.MANUAL);

		assertThat(withOldEvent.rule("login_tokens").changed()).isEqualTo(1);
		assertThat(withOldEvent.rule("account").matched()).isZero();
	}

	@Test
	void anAccountWithNoTraceOfActivityIsCountedFromTheFirstRun() {
		int silent = user("iz.yok@std.yildiz.edu.tr", null);
		int entry = whitelisted("sessiz@example.com", null);

		sweep.run(settings("apply", null), Trigger.MANUAL);

		assertThat(mailOf(silent)).isEqualTo("iz.yok@std.yildiz.edu.tr");
		assertThat(database.queryForObject("SELECT last_seen_at FROM users WHERE id = ?", Timestamp.class, silent)).isNotNull();

		clock.advance(Duration.ofDays(366));
		sweep.run(settings("apply", null), Trigger.MANUAL);

		assertThat(mailOf(silent)).isNull();
		assertThat(whitelistMail(entry)).isNull();
	}

	@Test
	void theBrakeRefusesARuleThatWouldChangeTooManyRows() {
		Instant eventEnd = now.minus(Duration.ofDays(100));
		int played = user("cok.piksel@std.yildiz.edu.tr", eventEnd);
		int pixel = pixel(1, 1, "00ff00");
		for (int i = 0; i < 3; i++) {
			pixelLog(pixel, played, "203.0.113.12", eventEnd.minus(Duration.ofMinutes(i)));
		}
		token(played, "203.0.113.12", eventEnd);

		RunResult dryRun = sweep.run(new RetentionSettings("dry-run", eventEnd.toString(), 2, 500), Trigger.MANUAL);
		RunResult apply = sweep.run(new RetentionSettings("apply", eventEnd.toString(), 2, 500), Trigger.MANUAL);

		assertThat(dryRun.rule("pixel_log_ip").status()).isEqualTo("would_refuse_large");
		assertThat(dryRun.status()).isEqualTo("ok");
		assertThat(apply.rule("pixel_log_ip").status()).isEqualTo("refused_large");
		assertThat(apply.rule("pixel_log_ip").changed()).isZero();
		assertThat(apply.status()).isEqualTo("partial");
		assertThat(database.queryForObject("SELECT count(*) FROM pixel_logs WHERE placer_ip IS NOT NULL", Integer.class)).isEqualTo(3);
		// The other rules still ran.
		assertThat(apply.rule("login_tokens").changed()).isEqualTo(1);
	}

	@Test
	void applyWorksInBatches() {
		Instant eventEnd = now.minus(Duration.ofDays(100));
		int played = user("parti@std.yildiz.edu.tr", eventEnd);
		int pixel = pixel(2, 2, "0000ff");
		for (int i = 0; i < 5; i++) {
			pixelLog(pixel, played, "203.0.113.13", eventEnd.minus(Duration.ofMinutes(i)));
		}

		RunResult apply = sweep.run(new RetentionSettings("apply", eventEnd.toString(), 50000, 2), Trigger.MANUAL);

		assertThat(apply.rule("pixel_log_ip").changed()).isEqualTo(5);
		assertThat(database.queryForObject("SELECT count(*) FROM pixel_logs WHERE placer_ip IS NOT NULL", Integer.class)).isZero();
	}

	@Test
	void aRunThatFindsTheLockTakenDoesNothing() throws Exception {
		Instant eventEnd = now.minus(Duration.ofDays(100));
		token(user("kilit@std.yildiz.edu.tr", eventEnd), "203.0.113.14", eventEnd);

		try (Connection other = dataSource.getConnection();
			 PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_lock(?)")) {
			lock.setLong(1, 0x706c616365726574L);
			lock.execute();
			try {
				RunResult result = sweep.run(settings("apply", eventEnd), Trigger.SCHEDULE);

				assertThat(result.outcome()).isEqualTo(Outcome.LOCKED);
			} finally {
				try (PreparedStatement unlock = other.prepareStatement("SELECT pg_advisory_unlock(?)")) {
					unlock.setLong(1, 0x706c616365726574L);
					unlock.execute();
				}
			}
		}

		assertThat(database.queryForObject("SELECT count(*) FROM user_tokens", Integer.class)).isEqualTo(1);
		assertThat(database.queryForObject("SELECT count(*) FROM retention_runs", Integer.class)).isZero();
		// Released: the next run takes it.
		assertThat(sweep.run(settings("apply", eventEnd), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.RAN);
	}

	@Test
	void theScheduleRunsOnceADayPerModeAndARunByHandDoesNotCount() {
		Instant eventEnd = now.minus(Duration.ofDays(100));

		assertThat(sweep.run(settings("dry-run", eventEnd), Trigger.MANUAL).outcome()).isEqualTo(Outcome.RAN);
		assertThat(sweep.run(settings("dry-run", eventEnd), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.RAN);
		assertThat(sweep.run(settings("dry-run", eventEnd), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.NOT_DUE);
		// A new EVENT_ENDED_AT makes it due again.
		assertThat(sweep.run(settings("dry-run", eventEnd.minus(Duration.ofDays(1))), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.RAN);
		// Switching to apply makes the apply run due the same day.
		assertThat(sweep.run(settings("apply", eventEnd), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.RAN);
		assertThat(sweep.run(settings("apply", eventEnd), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.NOT_DUE);

		clock.advance(Duration.ofHours(23).plusMinutes(1));

		assertThat(sweep.run(settings("apply", eventEnd), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.RAN);
		assertThat(sweep.run(settings("off", eventEnd), Trigger.SCHEDULE).outcome()).isEqualTo(Outcome.OFF);
	}

	@Test
	void aRunLeftRunningIsMarkedAbandonedAndEveryRunLeavesAReceipt() {
		database.update("INSERT INTO retention_runs (mode, triggered_by, started_at, status) VALUES ('apply', 'schedule', now(), 'running')");

		sweep.run(settings("dry-run", now.minus(Duration.ofDays(100))), Trigger.SCHEDULE);

		assertThat(database.queryForList("SELECT status FROM retention_runs ORDER BY id", String.class))
				.containsExactly("abandoned", "ok");
		assertThat(database.queryForObject("""
				SELECT count(*) FROM retention_run_rules r JOIN retention_runs u ON u.id = r.run_id WHERE u.status = 'ok'""", Integer.class))
				.isEqualTo(RetentionRules.ALL.size());
	}

	@Test
	void theLogHoldsCountsNeverAddressesOrIps(CapturedOutput output) {
		Instant eventEnd = now.minus(Duration.ofDays(400));
		int played = user("log.kisi@std.yildiz.edu.tr", eventEnd);
		token(played, "203.0.113.99", eventEnd);
		pixelLog(pixel(5, 5, "abcdef"), played, "203.0.113.99", eventEnd);

		sweep.run(settings("apply", eventEnd), Trigger.MANUAL);

		assertThat(output.getOut()).contains("retention_rule rule=pixel_log_ip version=1 mode=apply status=ok table=pixel_logs action=empty")
				.contains("matched=1 changed=1")
				.contains("retention_run id=")
				.doesNotContain("log.kisi")
				.doesNotContain("203.0.113.99");
	}

	private static RetentionSettings settings(String mode, Instant eventEnd) {
		return new RetentionSettings(mode, eventEnd == null ? "" : eventEnd.toString(), 50000, 500);
	}

	private static Map<String, Long> counts(RunResult result) {
		return result.rules().stream().collect(Collectors.toMap(RetentionSweep.RuleResult::rule, RetentionSweep.RuleResult::matched));
	}

	private String mailOf(int userId) {
		return database.queryForObject("SELECT school_mail FROM users WHERE id = ?", String.class, userId);
	}

	private Instant ago(int days) {
		return now.minus(Duration.ofDays(days));
	}

	private int user(String schoolMail, Instant lastPlacedAt) {
		return database.queryForObject("INSERT INTO users (school_mail, last_placed_at) VALUES (?, ?) RETURNING id",
				Integer.class, schoolMail, lastPlacedAt == null ? null : Timestamp.from(lastPlacedAt));
	}

	private int pixel(int x, int y, String color) {
		return database.queryForObject("INSERT INTO pixels (x, y, color) VALUES (?, ?, ?) RETURNING id", Integer.class, x, y, color);
	}

	private void pixelLog(int pixelId, int userId, String ip, Instant at) {
		database.update("INSERT INTO pixel_logs (pixel_id, user_id, new_color, placer_ip, placed_at) VALUES (?, ?, (SELECT color FROM pixels WHERE id = ?), ?, ?)",
				pixelId, userId, pixelId, ip, Timestamp.from(at));
	}

	private void token(int userId, String ip, Instant createdAt) {
		database.update("""
						INSERT INTO user_tokens (user_id, token_hash, user_ip, cloudflare_token, created_at, is_used, kind)
						VALUES (?, md5(random()::text), ?, 'turnstile-answer', ?, false, 'SESSION')""",
				userId, ip, Timestamp.from(createdAt));
	}

	private void bannedUser(int userId, Instant at) {
		database.update("INSERT INTO banned_users (banned_user_id, reason, banned_at) VALUES (?, 'neden', ?)", userId, Timestamp.from(at));
	}

	private void bannedIp(String ip, Instant at) {
		database.update("INSERT INTO banned_ips (ip, reason, banned_at) VALUES (?, 'neden', ?)", ip, Timestamp.from(at));
	}

	private void audit(String action, String target, String reason, Instant at) {
		database.update("INSERT INTO moderation_audit_log (action, target, reason, occurred_at) VALUES (?, ?, ?, ?)",
				action, target, reason, Timestamp.from(at));
	}

	private int whitelisted(String mail, Instant revokedAt) {
		return database.queryForObject("INSERT INTO whitelisted_mails (mail, revoked_at) VALUES (?, ?) RETURNING id",
				Integer.class, mail, revokedAt == null ? null : Timestamp.from(revokedAt));
	}

	private String whitelistMail(int id) {
		return database.queryForObject("SELECT mail FROM whitelisted_mails WHERE id = ?", String.class, id);
	}
}
