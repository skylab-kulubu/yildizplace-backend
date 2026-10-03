package com.weblab.rplace.weblab.rplace.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * An e-skylab login between the redirect to Keycloak and Keycloak's redirect back.
 * Short-lived and used once: the callback deletes it before looking at anything else.
 * Kept apart from user_tokens, which holds only mail links and Place sessions.
 */
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "eskylab_login_attempts")
public class EskylabLoginAttempt {

    // How long a login has between the redirect to Keycloak and the way back.
    public static final Duration LIFETIME = Duration.ofMinutes(10);

    // The OIDC state parameter.
    @Id
    @Column(name = "state")
    private String state;

    // SHA-256 of the browser cookie the login endpoint set: only that browser can finish the login.
    @Column(name = "browser_hash", nullable = false)
    private String browserHash;

    @Column(name = "nonce", nullable = false)
    private String nonce;

    // PKCE code verifier; Keycloak saw only its S256 challenge.
    @Column(name = "code_verifier", nullable = false)
    private String codeVerifier;

    // Where on the frontend the person goes afterwards; a path, checked when the login started.
    @Column(name = "return_path", nullable = false, length = 1024)
    private String returnPath;

    // Started with prompt=none.
    @Column(name = "silent", nullable = false)
    private boolean silent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
