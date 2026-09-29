package com.weblab.rplace.weblab.rplace.business.concretes;

import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.weblab.rplace.weblab.rplace.business.abstracts.BanService;
import com.weblab.rplace.weblab.rplace.business.abstracts.EskylabLoginService;
import com.weblab.rplace.weblab.rplace.business.abstracts.UserService;
import com.weblab.rplace.weblab.rplace.core.security.eskylab.EskylabClient;
import com.weblab.rplace.weblab.rplace.core.security.eskylab.EskylabLoginException;
import com.weblab.rplace.weblab.rplace.core.security.eskylab.Frontend;
import com.weblab.rplace.weblab.rplace.dataAccess.abstracts.EskylabLoginAttemptDao;
import com.weblab.rplace.weblab.rplace.entities.EskylabLoginAttempt;
import com.weblab.rplace.weblab.rplace.entities.User;
import com.weblab.rplace.weblab.rplace.entities.dtos.EskylabLoginResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EskylabLoginManager implements EskylabLoginService {

    private static final Logger log = LoggerFactory.getLogger(EskylabLoginManager.class);

    // A login has this long between the redirect to Keycloak and the way back.
    private static final Duration ATTEMPT_LIFETIME = Duration.ofMinutes(10);

    // The Place client's own claim (not "email", which may be a personal address; ADR 0060).
    private static final String SCHOOL_EMAIL_CLAIM = "school_email";

    // What Keycloak answers a prompt=none login when the person has no e-skylab session to use (OIDC Core 3.1.2.6).
    private static final Set<String> NO_ESKYLAB_SESSION = Set.of(
            "login_required", "interaction_required", "consent_required", "account_selection_required");

    private static final Pattern ERROR_CODE = Pattern.compile("[a-z_]{1,64}");

    private final EskylabClient keycloak;
    private final Frontend frontend;
    private final EskylabLoginAttemptDao attempts;
    private final Clock clock;

    private final UserService userService;

    private final BanService banService;

    private final boolean configured;

    public EskylabLoginManager(EskylabClient keycloak, Frontend frontend, EskylabLoginAttemptDao attempts, Clock clock,
                               UserService userService, BanService banService) {
        this.keycloak = keycloak;
        this.frontend = frontend;
        this.attempts = attempts;
        this.clock = clock;
        this.userService = userService;
        this.banService = banService;

        List<String> missing = new ArrayList<>(keycloak.missingSettings());
        if (!frontend.isConfigured()) {
            missing.add("PLACE_FRONTEND_URL");
        }
        this.configured = missing.isEmpty();
        if (!configured) {
            // Mail mode runs without it; only /api/auth/eskylab refuses.
            log.warn("e-skylab login is not configured, missing: {}. /api/auth/eskylab answers 503 until it is.", String.join(", ", missing));
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public URI start(String requestedReturnPath, boolean silent, String browser) {
        String returnPath = frontend.returnPath(requestedReturnPath);
        Instant now = clock.instant();
        attempts.deleteCreatedBefore(now.minus(ATTEMPT_LIFETIME));

        var state = new State();
        var nonce = new Nonce();
        var codeVerifier = new CodeVerifier();
        URI authorizationRequest;
        try {
            authorizationRequest = keycloak.authorizationRequest(state, nonce, codeVerifier, silent);
        } catch (EskylabLoginException e) {
            log.warn("e-skylab login could not start: {}", e.getMessage());
            return frontend.url(returnPath, "error");
        }

        attempts.save(EskylabLoginAttempt.builder()
                .state(state.getValue())
                .browserHash(hash(browser))
                .nonce(nonce.getValue())
                .codeVerifier(codeVerifier.getValue())
                .returnPath(returnPath)
                .silent(silent)
                .createdAt(now)
                .build());
        return authorizationRequest;
    }

    @Override
    public EskylabLoginResult finish(String state, String code, String error, String browser) {
        EskylabLoginAttempt attempt;
        try {
            attempt = take(state, browser);
        } catch (EskylabLoginException e) {
            return refused(e, "/");
        }
        String returnPath = attempt.getReturnPath();

        if (error != null) {
            if (attempt.isSilent() && NO_ESKYLAB_SESSION.contains(error)) {
                return new EskylabLoginResult(null, frontend.url(returnPath, "none"));
            }
            return refused(new EskylabLoginException("Keycloak answered " + loggable(error)), returnPath);
        }

        try {
            if (code == null || code.isBlank()) {
                throw new EskylabLoginException("callback without code");
            }
            IDTokenClaimsSet claims = keycloak.redeem(code, new CodeVerifier(attempt.getCodeVerifier()), new Nonce(attempt.getNonce()));
            return new EskylabLoginResult(admit(claims), frontend.url(returnPath, null));
        } catch (EskylabLoginException e) {
            return refused(e, returnPath);
        }
    }

    // The attempt this callback belongs to, removed so that it works only once.
    private EskylabLoginAttempt take(String state, String browser) throws EskylabLoginException {
        if (state == null || state.isBlank()) {
            throw new EskylabLoginException("callback without state");
        }
        EskylabLoginAttempt attempt = attempts.findById(state)
                .orElseThrow(() -> new EskylabLoginException("unknown, used or expired state"));
        if (attempts.deleteByState(state) != 1) {
            throw new EskylabLoginException("state already used");
        }
        if (attempt.getCreatedAt().isBefore(clock.instant().minus(ATTEMPT_LIFETIME))) {
            throw new EskylabLoginException("login attempt expired");
        }
        if (browser == null || !MessageDigest.isEqual(
                hash(browser).getBytes(StandardCharsets.US_ASCII), attempt.getBrowserHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new EskylabLoginException("state was issued to another browser");
        }
        return attempt;
    }

    // The Place account of the school address in the ID token, checked like the mail registration checks it.
    private User admit(IDTokenClaimsSet claims) throws EskylabLoginException {
        String schoolEmail = claims.getStringClaim(SCHOOL_EMAIL_CLAIM);
        if (schoolEmail == null || schoolEmail.isBlank()) {
            throw new EskylabLoginException("ID token has no " + SCHOOL_EMAIL_CLAIM);
        }
        String schoolMail = User.normalizeSchoolMail(schoolEmail);
        if (!userService.isSchoolMailAllowed(schoolMail)) {
            throw new EskylabLoginException(SCHOOL_EMAIL_CLAIM + " is not a school address");
        }
        if (banService.isUserBanned(schoolMail).isSuccess()) {
            throw new EskylabLoginException("the user is banned");
        }
        return userService.findOrCreateUser(schoolMail);
    }

    private EskylabLoginResult refused(EskylabLoginException reason, String returnPath) {
        log.warn("e-skylab login refused: {}", reason.getMessage());
        return new EskylabLoginResult(null, frontend.url(returnPath, "error"));
    }

    // The error parameter comes from the browser: log it only when it looks like an OAuth error code.
    private static String loggable(String error) {
        return ERROR_CODE.matcher(error).matches() ? error : "an unexpected error value";
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
