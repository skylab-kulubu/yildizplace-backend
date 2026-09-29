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
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Place's own session, the same whichever way a person logged in: a user_tokens row
 * (kind SESSION, with its source) behind the user_token cookie. The mail login and
 * the e-skylab login both open it here, and logout ends it here.
 *
 * <p>A session is ROLE_USER and lasts as long as its cookie, a year. An elevated
 * session, one opened with e-skylab by a person with a Place client role (ADR 0060),
 * also carries ROLE_ADMIN or ROLE_MODERATOR and ends PLACE_ELEVATED_SESSION_TTL after
 * the login; its cookies (user_token and isAdmin) last just as long.
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

    public PlaceSessions(UserTokenService userTokenService, Clock clock, @Value("${domain}") String domain,
                         @Value("${place.elevated-session-ttl}") Duration elevatedSessionTtl) {
        this.userTokenService = userTokenService;
        this.clock = clock;
        this.domain = domain;
        this.elevatedSessionTtl = elevatedSessionTtl;
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
                .token(sessionToken)
                .userId(user.getId())
                .createdAt(Date.from(now))
                .kind(UserTokenKind.SESSION)
                .source(source)
                .role(elevated ? role : null)
                .expiresAt(elevated ? now.plus(elevatedSessionTtl) : null)
                .build());

        int maxAge = elevated ? (int) elevatedSessionTtl.toSeconds() : COOKIE_MAX_AGE_SECONDS;
        response.addCookie(cookie(SESSION_COOKIE, sessionToken, maxAge));
        response.addCookie(elevated ? cookie(ADMIN_COOKIE, "true", maxAge) : cookie(ADMIN_COOKIE, "", 0));
    }

    /** Ends the Place session of this request, if it has one, and deletes the login cookies. Keycloak is not told. */
    public void end(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = sessionToken(request);
        if (sessionToken != null) {
            userTokenService.endSession(sessionToken);
        }
        response.addCookie(cookie(SESSION_COOKIE, "", 0));
        response.addCookie(cookie(ADMIN_COOKIE, "", 0));
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

    private Cookie cookie(String name, String value, int maxAge) {
        var cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setDomain(domain);
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        return cookie;
    }
}
