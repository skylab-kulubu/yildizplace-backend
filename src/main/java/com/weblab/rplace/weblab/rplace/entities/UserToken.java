package com.weblab.rplace.weblab.rplace.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "user_tokens")
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    /*
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
     */

    @Column(name = "user_id")
    private int userId;

    @Column(name = "token")
    private String token;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "cloudflare_token", length = 2048)
    private String cloudflareToken;

    @Column(name = "user_ip")
    private String userIp;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "is_used")
    private boolean isUsed;

    @Column(name = "used_at")
    private Date usedAt;

    // Null on rows from before links and sessions were told apart: those are sessions.
    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    private UserTokenKind kind;

    // Sessions only: how the session was opened. Null on sessions from before this column
    // existed (mail logins) and on login links. Its own column, not new kinds: ddl-auto
    // does not widen the check constraint of an existing enum column.
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private SessionSource source;

    // Elevated sessions only: ROLE_ADMIN or ROLE_MODERATOR, from the e-skylab login's Keycloak
    // client roles (ADR 0060). Null on every other session, and a session without a role is
    // ROLE_USER: mail sessions, sessions from before this column existed, e-skylab sessions
    // without a Place role. The authorities table does not count.
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    // Elevated sessions only: when the session ends (PLACE_ELEVATED_SESSION_TTL after the login).
    // Null: the session does not end on the server; its cookie lasts a year.
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** What this session may do: ROLE_USER, and its role if it is an elevated session. */
    public Set<Role> grantedRoles() {
        return role == null ? Set.of(Role.ROLE_USER) : Set.of(Role.ROLE_USER, role);
    }

    /** Whether the session is over at this moment; only elevated sessions have an end. */
    public boolean hasEndedBy(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

}
