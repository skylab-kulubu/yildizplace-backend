package com.weblab.rplace.weblab.rplace.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One moderation action: a ban, an unban, a whitelist entry revoked or restored.
 * Written once and never changed or deleted, so a lifted ban (whose banned_users or
 * banned_ips row is gone) still shows who made and who lifted it, and why.
 *
 * <p>action and actor_role are plain strings, not enum columns: Hibernate's
 * ddl-auto=update would not widen an enum check constraint when an action is added.
 */
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "moderation_audit_log")
public class ModerationAuditEntry {

    public static final String BAN_USER = "BAN_USER";
    public static final String UNBAN_USER = "UNBAN_USER";
    public static final String BAN_IP = "BAN_IP";
    public static final String UNBAN_IP = "UNBAN_IP";
    public static final String REVOKE_WHITELISTED_MAIL = "REVOKE_WHITELISTED_MAIL";
    public static final String RESTORE_WHITELISTED_MAIL = "RESTORE_WHITELISTED_MAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    // The school address, IP address or whitelisted address acted on.
    @Column(name = "target", nullable = false, length = 320)
    private String target;

    @Column(name = "reason", length = 1024)
    private String reason;

    // The Place user who did it, and the role of the session they did it with.
    @Column(name = "actor_user_id")
    private Integer actorUserId;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(name = "occurred_at", nullable = false)
    private Instant at;
}
