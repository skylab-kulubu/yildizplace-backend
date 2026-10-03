package com.weblab.rplace.weblab.rplace.business.abstracts;

import com.weblab.rplace.weblab.rplace.entities.dtos.EskylabLoginResult;

import java.net.URI;

/**
 * Logging in with e-skylab (ADR 0060). Open in every login mode: admins and
 * moderators always log in this way.
 */
public interface EskylabLoginService {

    /** False until every setting the login needs is there; the endpoints then say so. */
    boolean isConfigured();

    /**
     * Starts a login for the browser holding the given cookie value and returns where
     * that browser goes next: Keycloak's authorization endpoint, or the frontend with
     * ?sso=error when Keycloak cannot be reached.
     */
    URI start(String returnPath, boolean silent, String browser);

    /**
     * Finishes the login Keycloak sent the browser back from. Logged in: the user,
     * and the return path on the frontend. With prompt=none and no e-skylab session:
     * no user, and the frontend with ?sso=none. Anything else: no user, and the
     * frontend with ?sso=error; the reason goes to the log. A banned user, or a user
     * without a Place role at a banned address: no user, and the frontend with ?sso=banned.
     */
    EskylabLoginResult finish(String state, String code, String error, String browser, String clientIp);
}
