package com.weblab.rplace.weblab.rplace.core.security;

import com.weblab.rplace.weblab.rplace.business.abstracts.UserTokenService;
import com.weblab.rplace.weblab.rplace.entities.Role;
import com.weblab.rplace.weblab.rplace.entities.SessionSource;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.UserToken;
import com.weblab.rplace.weblab.rplace.entities.UserTokenKind;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

/**
 * Place's own session, the same whichever way a person logged in: a user_tokens row
 * (kind SESSION, with its source) behind the user_token cookie. The mail login and
 * the e-skylab login both open it here, and logout ends it here.
 *
 * <p>A session is ROLE_USER and lasts as long as its cookie, a year. An elevated
 * session, one opened with e-skylab by a person with a Place client role (ADR 0060),
 * also carries ROLE_ADMIN or ROLE_MODERATOR and ends PLACE_ELEVATED_SESSION_TTL after
 * the login; its cookies (user_token and isAdmin) last just as long.
 *
 * <p>The row keeps only the SHA-256 of the cookie's value (TokenHashes). Both cookies
 * are HttpOnly, Secure and SameSite=Lax, also when they are deleted. Lax, not Strict:
 * the frontend reads them on top-level navigations, also from a link on another site
 * (a mail client), and Strict would leave them out there.
 *
 * <p>Moving the API to another host (ADR 0090): the frontend's middleware reads the
 * cookies on the frontend's own host, so their Domain (DOMAIN) must cover both hosts.
 * While the frontend still calls the old API host, PLACE_LEGACY_COOKIE_DOMAIN names the
 * Domain the cookies had before. A request to a host under it with a live session gets
 * the same cookies on DOMAIN (with the time they had left) and the old ones deleted;
 * logout there deletes both. Off when blank.
 */
@Component
public class PlaceSessions {

    public static final String SESSION_COOKIE = "user_token";

    private static final String ADMIN_COOKIE = "isAdmin";

    private static final int COOKIE_MAX_AGE_SECONDS = 31536000;

    private final UserTokenService userTokenService;

    private final Clock clock;

    private final String domain;

    private final Duration elevatedSessionTtl;

    private final String legacyDomain;

    public PlaceSessions(UserTokenService userTokenService, Clock clock, @Value("${domain}") String domain,
                         @Value("${place.elevated-session-ttl}") Duration elevatedSessionTtl,
                         @Value("${place.legacy-cookie-domain:}") String legacyDomain) {
        this.userTokenService = userTokenService;
        this.clock = clock;
        this.domain = domain;
        this.elevatedSessionTtl = elevatedSessionTtl;
        this.legacyDomain = legacyDomain == null ? "" : legacyDomain.trim().toLowerCase(Locale.ROOT);
        // The old Domain must sit under the new one: a cookie on DOMAIN then reaches the old host
        // too, and deleting the old cookies never deletes the new ones.
        if (!this.legacyDomain.isEmpty() && (domain == null
                || !this.legacyDomain.endsWith("." + domain.trim().toLowerCase(Locale.ROOT)))) {
            throw new IllegalStateException("PLACE_LEGACY_COOKIE_DOMAIN must be a subdomain of DOMAIN");
        }
    }

    /** Opens a mail session: always ROLE_USER, whatever the authorities table says. */
    public void openMailSession(User user, HttpServletResponse response) {
        open(user, SessionSource.MAIL, Role.ROLE_USER, response);
    }

    /** Opens an e-skylab session with the role from the person's Place client roles. */
    public void openEskylabSession(User user, Role role, HttpServletResponse response) {
        open(user, SessionSource.ESKYLAB, role, response);
    }

    // A new random value in the user_token cookie. Only an elevated session has an end and the isAdmin
    // cookie; any other login deletes an isAdmin cookie an earlier session may have left.
    private void open(User user, SessionSource source, Role role, HttpServletResponse response) {
        boolean elevated = role != Role.ROLE_USER;
        Instant now = clock.instant();
        String sessionToken = RandomTokens.generate();
        userTokenService.addToken(UserToken.builder()
                .tokenHash(TokenHashes.sha256Hex(sessionToken))
                .userId(user.getId())
                .createdAt(Date.from(now))
                .kind(UserTokenKind.SESSION)
                .source(source)
                .role(elevated ? role : null)
                .expiresAt(elevated ? now.plus(elevatedSessionTtl) : null)
                .build());

        int maxAge = elevated ? (int) elevatedSessionTtl.toSeconds() : COOKIE_MAX_AGE_SECONDS;
        addCookie(response, SESSION_COOKIE, sessionToken, maxAge);
        if (elevated) {
            addCookie(response, ADMIN_COOKIE, "true", maxAge);
        } else {
            addCookie(response, ADMIN_COOKIE, "", 0);
        }
    }

    /** Ends the Place session of this request, if it has one, and deletes the login cookies. Keycloak is not told. */
    public void end(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = sessionToken(request);
        if (sessionToken != null) {
            userTokenService.endSession(sessionToken);
        }
        addCookie(response, SESSION_COOKIE, "", 0);
        addCookie(response, ADMIN_COOKIE, "", 0);
        if (onLegacyHost(request)) {
            deleteLegacyCookies(response);
        }
    }

    /**
     * On a host under PLACE_LEGACY_COOKIE_DOMAIN: sets this live session's cookies again on
     * DOMAIN, with the time they have left, and deletes the ones on the old Domain. Anywhere
     * else, or when the setting is blank, it does nothing.
     */
    public void moveFromLegacyDomain(HttpServletRequest request, String sessionToken, UserToken session,
                                     HttpServletResponse response) {
        if (!onLegacyHost(request)) {
            return;
        }
        Instant end;
        if (session.getExpiresAt() != null) {
            end = session.getExpiresAt();
        } else if (session.getCreatedAt() != null) {
            end = session.getCreatedAt().toInstant().plusSeconds(COOKIE_MAX_AGE_SECONDS);
        } else {
            end = clock.instant().plusSeconds(COOKIE_MAX_AGE_SECONDS);
        }
        // To the nearest second: the stored end has microseconds, the clock nanoseconds.
        long left = (Duration.between(clock.instant(), end).toMillis() + 500) / 1000;
        if (left > 0) {
            int maxAge = (int) Math.min(left, COOKIE_MAX_AGE_SECONDS);
            addCookie(response, SESSION_COOKIE, sessionToken, maxAge);
            if (session.getRole() != null) {
                addCookie(response, ADMIN_COOKIE, "true", maxAge);
            }
        }
        deleteLegacyCookies(response);
    }

    private boolean onLegacyHost(HttpServletRequest request) {
        if (legacyDomain.isEmpty() || request.getServerName() == null) {
            return false;
        }
        String host = request.getServerName().toLowerCase(Locale.ROOT);
        return host.equals(legacyDomain) || host.endsWith("." + legacyDomain);
    }

    private void deleteLegacyCookies(HttpServletResponse response) {
        addCookie(response, SESSION_COOKIE, "", 0, legacyDomain);
        addCookie(response, ADMIN_COOKIE, "", 0, legacyDomain);
    }

    /** The value of the request's user_token cookie, or null. */
    public static String sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String token = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(SESSION_COOKIE)) {
                token = cookie.getValue();
            }
        }
        return token;
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        addCookie(response, name, value, maxAge, domain);
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge, String cookieDomain) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .path("/")
                .domain(cookieDomain)
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .build().toString());
    }
}
