package com.weblab.rplace.weblab.rplace.webAPI.controllers;

import com.weblab.rplace.weblab.rplace.business.abstracts.EskylabLoginService;
import com.weblab.rplace.weblab.rplace.business.constants.Messages;
import com.weblab.rplace.weblab.rplace.core.security.PlaceSessions;
import com.weblab.rplace.weblab.rplace.core.security.RandomTokens;
import com.weblab.rplace.weblab.rplace.core.utilities.results.ErrorResult;
import com.weblab.rplace.weblab.rplace.core.utilities.results.Result;
import com.weblab.rplace.weblab.rplace.entities.SessionSource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * The e-skylab login as a browser sees it (ADR 0060): /login sends it to Keycloak,
 * Keycloak sends it back to /callback, and the callback sends it to the frontend,
 * logged in with the user_token cookie or with ?sso=none / ?sso=error.
 */
@RestController
@RequestMapping("api/auth/eskylab")
@RequiredArgsConstructor
public class EskylabLoginController {

    // Ties a login to the browser that started it (against login CSRF). The __Host- prefix
    // keeps other subdomains from setting it; SameSite=Lax lets it ride Keycloak's redirect back.
    static final String BROWSER_COOKIE = "__Host-place_eskylab";

    private static final Duration BROWSER_COOKIE_LIFETIME = Duration.ofMinutes(10);
    private static final Pattern BROWSER_VALUE = Pattern.compile("[A-Za-z0-9_-]{22}==");

    private final EskylabLoginService eskylabLoginService;

    private final PlaceSessions placeSessions;

    @GetMapping("/login")
    public ResponseEntity<Result> login(@RequestParam(required = false) String prompt,
                                        @RequestParam(required = false) String returnTo,
                                        @CookieValue(name = BROWSER_COOKIE, required = false) String browser,
                                        HttpServletResponse response) {
        if (!eskylabLoginService.isConfigured()) {
            return notConfigured();
        }

        // Tabs logging in at the same time share the value, so none of them spoils another's login.
        String browserValue = browser != null && BROWSER_VALUE.matcher(browser).matches() ? browser : RandomTokens.generate();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(BROWSER_COOKIE, browserValue)
                .path("/")
                .maxAge(BROWSER_COOKIE_LIFETIME)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .build().toString());

        return redirect(eskylabLoginService.start(returnTo, "none".equals(prompt), browserValue));
    }

    // Keycloak's tokens stay in the backend: the browser gets Place's session cookie and a redirect.
    @GetMapping("/callback")
    public ResponseEntity<Result> callback(@RequestParam(required = false) String state,
                                           @RequestParam(required = false) String code,
                                           @RequestParam(required = false) String error,
                                           @CookieValue(name = BROWSER_COOKIE, required = false) String browser,
                                           HttpServletResponse response) {
        if (!eskylabLoginService.isConfigured()) {
            return notConfigured();
        }

        var result = eskylabLoginService.finish(state, code, error, browser);
        if (result.user() != null) {
            placeSessions.open(result.user(), SessionSource.ESKYLAB, response);
        }
        return redirect(result.redirect());
    }

    private static ResponseEntity<Result> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private static ResponseEntity<Result> notConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResult(Messages.eskylabLoginNotConfigured));
    }
}
