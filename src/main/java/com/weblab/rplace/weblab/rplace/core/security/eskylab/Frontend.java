package com.weblab.rplace.weblab.rplace.core.security.eskylab;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Objects;

/**
 * Place's frontend (PLACE_FRONTEND_URL), where every e-skylab login ends. A login
 * may name a path to come back to; anything that is not a plain path on this
 * frontend becomes "/", so the login can never send a browser to another site.
 */
@Component
public class Frontend {

    private static final int MAX_RETURN_PATH = 1024;

    private final String base;

    public Frontend(@Value("${place.frontend-url:}") String base) {
        String trimmed = base.trim();
        this.base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public boolean isConfigured() {
        return !base.isEmpty();
    }

    /** The requested path if it is a path on the frontend, else "/". */
    public String returnPath(String requested) {
        if (requested == null || requested.isEmpty() || requested.length() > MAX_RETURN_PATH
                || !requested.startsWith("/") || requested.startsWith("//")
                || requested.chars().anyMatch(c -> c == '\\' || c < 0x20 || c == 0x7f)) {
            return "/";
        }
        try {
            URI frontend = URI.create(base);
            URI target = URI.create(base + requested);
            boolean sameOrigin = Objects.equals(frontend.getScheme(), target.getScheme())
                    && Objects.equals(frontend.getHost(), target.getHost())
                    && frontend.getPort() == target.getPort();
            return sameOrigin ? requested : "/";
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    /** The frontend at a checked return path, with ?sso=<flag> when the flag is not null. */
    public URI url(String returnPath, String ssoFlag) {
        var url = UriComponentsBuilder.fromUriString(base + returnPath);
        if (ssoFlag != null) {
            url.replaceQueryParam("sso", ssoFlag);
        }
        return url.build(true).toUri();
    }
}
