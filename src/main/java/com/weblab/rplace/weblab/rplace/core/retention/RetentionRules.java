package com.weblab.rplace.weblab.rplace.core.retention;

import com.weblab.rplace.weblab.rplace.core.retention.RetentionRule.Action;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionRule.Cutoff;

import java.util.List;

/**
 * Place's rules (ADR-0062, the Place and Guessr rows), with sliding periods: Place runs
 * all the time, so a period counts from the row's own time, or from the person's last
 * activity (last login, last pixel), whichever is latest. EVENT_ENDED_AT is optional:
 * when it is set, a period counts from it too, if it is later. A row with no time at all
 * is left alone.
 *
 * <p>Never touched: the canvas (pixels, final_pixels) and what was placed where and
 * when (pixel_logs without its IP). A pixel log keeps its user_id; once the account's
 * school address is emptied, that user is a number that names nobody.
 */
public final class RetentionRules {

    private RetentionRules() {
    }

    private static final String EVENT_END = "CAST(:eventEnd AS timestamptz)";

    // GREATEST skips nulls: without EVENT_ENDED_AT the row's own time decides.
    private static String due(String rowTime, Cutoff cutoff) {
        return "greatest(" + EVENT_END + ", " + rowTime + ") <= CAST(:" + cutoff.parameter() + " AS timestamptz)";
    }

    private static String stays(String rowTime, Cutoff cutoff) {
        return "greatest(" + EVENT_END + ", " + rowTime + ") > CAST(:" + cutoff.parameter() + " AS timestamptz)";
    }

    // A moderation record counts from the last record about the same target in the same pair of
    // actions: a ban's record from the ban's end (the unban), or from the ban itself while none came.
    private static String lastRecordOn(String actions) {
        return "(SELECT max(m2.occurred_at) FROM moderation_audit_log m2 WHERE m2.target = m.target AND m2.action IN ("
                + actions + "))";
    }

    // The person's last activity: last pixel, last login (a login token's creation; last_seen_at
    // keeps it after the login_tokens rule has removed the token, RetentionSweep).
    private static final String LAST_ACTIVITY = "greatest(u.last_placed_at, u.last_seen_at, "
            + "(SELECT max(t.created_at) FROM user_tokens t WHERE t.user_id = u.id))";

    public static final List<RetentionRule> ALL = List.of(
            // Login links and sessions (the value's hash, the IP, the Turnstile answer): 90 days
            // after the row was written. A session older than that ends; the person logs in again.
            new RetentionRule("login_tokens", 1, "user_tokens", "t", Action.DELETE, null, Cutoff.IP,
                    due("t.created_at", Cutoff.IP)),
            // The pixel log stays (who placed what, where, when); only its IP goes.
            new RetentionRule("pixel_log_ip", 1, "pixel_logs", "pl", Action.EMPTY, "placer_ip = NULL", Cutoff.IP,
                    "pl.placer_ip IS NOT NULL AND " + due("pl.placed_at", Cutoff.IP)),
            // An IP ban is an IP: it ends 90 days after it was made.
            new RetentionRule("banned_ip", 1, "banned_ips", "bi", Action.DELETE, null, Cutoff.IP,
                    due("bi.banned_at", Cutoff.IP)),
            new RetentionRule("audit_ip_target", 1, "moderation_audit_log", "m", Action.EMPTY,
                    "target = '', reason = NULL", Cutoff.IP,
                    "m.action IN ('BAN_IP', 'UNBAN_IP') AND (m.target <> '' OR m.reason IS NOT NULL) AND "
                            + due(lastRecordOn("'BAN_IP', 'UNBAN_IP'"), Cutoff.IP)
                            + " AND NOT EXISTS (SELECT 1 FROM banned_ips bi WHERE bi.ip = m.target AND "
                            + stays("bi.banned_at", Cutoff.IP) + ")"),
            // A ban on an account has no end in Place (an unban deletes the row): two years from
            // when it was made. The account rule below then empties the address, once its own
            // period has passed.
            new RetentionRule("banned_account", 1, "banned_users", "b", Action.DELETE, null, Cutoff.BAN,
                    due("b.banned_at", Cutoff.BAN)),
            // The ban's records: two years from the ban's end (the unban), or from the ban while it
            // stands; never while a ban on that address stands.
            new RetentionRule("audit_banned_target", 1, "moderation_audit_log", "m", Action.EMPTY,
                    "target = '', reason = NULL", Cutoff.BAN,
                    "m.action IN ('BAN_USER', 'UNBAN_USER') AND (m.target <> '' OR m.reason IS NOT NULL) AND "
                            + due(lastRecordOn("'BAN_USER', 'UNBAN_USER'"), Cutoff.BAN)
                            + " AND NOT EXISTS (SELECT 1 FROM banned_users b JOIN users bu ON bu.id = b.banned_user_id"
                            + " WHERE bu.school_mail = m.target AND " + stays("b.banned_at", Cutoff.BAN) + ")"),
            // The account: a year after the person's last activity. Never while a ban on it stands,
            // nor while it has a login token the login_tokens rule keeps (active in the last 90
            // days); both read the other rules' cutoffs, so the dry run and apply count the same
            // rows whatever the order.
            new RetentionRule("account", 1, "users", "u", Action.EMPTY, "school_mail = NULL", Cutoff.ACCOUNT,
                    "u.school_mail IS NOT NULL AND " + due(LAST_ACTIVITY, Cutoff.ACCOUNT)
                            + " AND NOT EXISTS (SELECT 1 FROM banned_users b WHERE b.banned_user_id = u.id AND "
                            + stays("b.banned_at", Cutoff.BAN) + ")"
                            + " AND NOT EXISTS (SELECT 1 FROM user_tokens t WHERE t.user_id = u.id AND "
                            + stays("t.created_at", Cutoff.IP) + ")"),
            // The whitelist keeps its rows (ADR 0042); the address goes a year after the entry's last
            // change (revoked, restored), or, for an entry never changed, after the first run that saw
            // it. No login reads the whitelist today.
            new RetentionRule("whitelist_mail", 1, "whitelisted_mails", "w", Action.EMPTY, "mail = NULL", Cutoff.ACCOUNT,
                    "w.mail IS NOT NULL AND " + due("coalesce(greatest(w.revoked_at, "
                            + "(SELECT max(m.occurred_at) FROM moderation_audit_log m WHERE m.target = w.mail"
                            + " AND m.action IN ('REVOKE_WHITELISTED_MAIL', 'RESTORE_WHITELISTED_MAIL'))), w.retention_seen_at)", Cutoff.ACCOUNT)),
            new RetentionRule("audit_whitelist_target", 1, "moderation_audit_log", "m", Action.EMPTY,
                    "target = '', reason = NULL", Cutoff.ACCOUNT,
                    "m.action IN ('REVOKE_WHITELISTED_MAIL', 'RESTORE_WHITELISTED_MAIL') AND (m.target <> '' OR m.reason IS NOT NULL) AND "
                            + due(lastRecordOn("'REVOKE_WHITELISTED_MAIL', 'RESTORE_WHITELISTED_MAIL'"), Cutoff.ACCOUNT)));
}
