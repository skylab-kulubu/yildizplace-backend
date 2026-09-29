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

import java.util.Date;

/**
 * Place's own session, the same whichever way a person logged in: a user_tokens row
 * (kind SESSION, with its source) behind the user_token cookie. The mail login and
 * the e-skylab login both open it here, and logout ends it here.
 */
@Component
public class PlaceSessions {

    public static final String SESSION_COOKIE = "user_token";

    private static final String ADMIN_COOKIE = "isAdmin";

    private static final int COOKIE_MAX_AGE_SECONDS = 31536000;

    private final UserTokenService userTokenService;

    private final String domain;

    public PlaceSessions(UserTokenService userTokenService, @Value("${domain}") String domain) {
        this.userTokenService = userTokenService;
        this.domain = domain;
    }

    /** Opens a session for the user under a new random value and sets the login cookies. */
    public void open(User user, SessionSource source, HttpServletResponse response) {
        String sessionToken = RandomTokens.generate();
        userTokenService.addToken(UserToken.builder()
                .token(sessionToken)
                .userId(user.getId())
                .createdAt(new Date())
                .kind(UserTokenKind.SESSION)
                .source(source)
                .build());

        response.addCookie(cookie(SESSION_COOKIE, sessionToken, COOKIE_MAX_AGE_SECONDS));

        // Follows the roles in Place's database, as before; ticket 04 moves this to Keycloak's roles.
        if (user.getAuthorities().contains(Role.ROLE_ADMIN) || user.getAuthorities().contains(Role.ROLE_MODERATOR)) {
            response.addCookie(cookie(ADMIN_COOKIE, "true", COOKIE_MAX_AGE_SECONDS));
        }
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
