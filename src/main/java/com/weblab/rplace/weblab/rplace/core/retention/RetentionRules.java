package com.weblab.rplace.weblab.rplace.core.retention;

import com.weblab.rplace.weblab.rplace.core.retention.RetentionRule.Action;
import com.weblab.rplace.weblab.rplace.core.retention.RetentionRule.Cutoff;

import java.util.List;

/**
 * Place's rules (ADR-0062, the Place and Guessr rows). Every period counts from the
 * event's end (EVENT_ENDED_AT), or from the row's own time when that is later (a row
 * written after the event, such as a moderator's later ban, gets its full period).
 *
 * <p>Never touched: the canvas (pixels, final_pixels) and what was placed where and
 * when (pixel_logs without its IP). A pixel log keeps its user_id; once the account's
 * school address is emptied, that user is a number that names nobody.
 */
public final class RetentionRules {

    private RetentionRules() {
    }

    private static final String EVENT_END = "CAST(:eventEnd AS timestamptz)";

    private static String due(String rowTime, Cutoff cutoff) {
        return "greatest(" + EVENT_END + ", " + rowTime + ") <= CAST(:" + cutoff.parameter() + " AS timestamptz)";
    }

    private static String stays(String rowTime, Cutoff cutoff) {
        return "greatest(" + EVENT_END + ", " + rowTime + ") > CAST(:" + cutoff.parameter() + " AS timestamptz)";
    }

    public static final List<RetentionRule> ALL = List.of(
            // Login links and sessions: the value's hash, the IP and the Turnstile answer.
            new RetentionRule("login_tokens", 1, "user_tokens", "t", Action.DELETE, null, Cutoff.IP,
                    due("t.created_at", Cutoff.IP)),
            // The pixel log stays (who placed what, where, when); only its IP goes.
            new RetentionRule("pixel_log_ip", 1, "pixel_logs", "pl", Action.EMPTY, "placer_ip = NULL", Cutoff.IP,
                    "pl.placer_ip IS NOT NULL AND " + due("pl.placed_at", Cutoff.IP)),
            // An IP ban is an IP: 90 days.
            new RetentionRule("banned_ip", 1, "banned_ips", "bi", Action.DELETE, null, Cutoff.IP,
                    due("bi.banned_at", Cutoff.IP)),
            new RetentionRule("audit_ip_target", 1, "moderation_audit_log", "m", Action.EMPTY,
                    "target = '', reason = NULL", Cutoff.IP,
                    "m.action IN ('BAN_IP', 'UNBAN_IP') AND (m.target <> '' OR m.reason IS NOT NULL) AND "
                            + due("m.occurred_at", Cutoff.IP)),
            // A banned account (not an IP): two years. The ban ends with its row; the account
            // rule below then empties the address.
            new RetentionRule("banned_account", 1, "banned_users", "b", Action.DELETE, null, Cutoff.BAN,
                    due("b.banned_at", Cutoff.BAN)),
            new RetentionRule("audit_banned_target", 1, "moderation_audit_log", "m", Action.EMPTY,
                    "target = '', reason = NULL", Cutoff.BAN,
                    "m.action IN ('BAN_USER', 'UNBAN_USER') AND (m.target <> '' OR m.reason IS NOT NULL) AND "
                            + due("m.occurred_at", Cutoff.BAN)),
            // The account: a year from the event or the person's last pixel. Left out while a ban
            // on it stands, and while it has a login token the login_tokens rule keeps (someone
            // who logged in within the last 90 days); both conditions read the other rules'
            // cutoffs, so the dry run and apply count the same rows whatever the order.
            new RetentionRule("account", 1, "users", "u", Action.EMPTY, "school_mail = NULL", Cutoff.ACCOUNT,
                    "u.school_mail IS NOT NULL AND " + due("u.last_placed_at", Cutoff.ACCOUNT)
                            + " AND NOT EXISTS (SELECT 1 FROM banned_users b WHERE b.banned_user_id = u.id AND "
                            + stays("b.banned_at", Cutoff.BAN) + ")"
                            + " AND NOT EXISTS (SELECT 1 FROM user_tokens t WHERE t.user_id = u.id AND "
                            + stays("t.created_at", Cutoff.IP) + ")"),
            // The whitelist keeps its rows (ADR 0042); the address goes.
            new RetentionRule("whitelist_mail", 1, "whitelisted_mails", "w", Action.EMPTY, "mail = NULL", Cutoff.ACCOUNT,
                    "w.mail IS NOT NULL AND " + due("w.revoked_at", Cutoff.ACCOUNT)),
            new RetentionRule("audit_whitelist_target", 1, "moderation_audit_log", "m", Action.EMPTY,
                    "target = '', reason = NULL", Cutoff.ACCOUNT,
                    "m.action IN ('REVOKE_WHITELISTED_MAIL', 'RESTORE_WHITELISTED_MAIL') AND (m.target <> '' OR m.reason IS NOT NULL) AND "
                            + due("m.occurred_at", Cutoff.ACCOUNT)));
}
