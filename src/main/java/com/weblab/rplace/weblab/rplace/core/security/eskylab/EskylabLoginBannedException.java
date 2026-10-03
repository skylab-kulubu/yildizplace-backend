package com.weblab.rplace.weblab.rplace.core.security.eskylab;

/** A login refused because the user or the address is banned; the frontend gets ?sso=banned. */
public class EskylabLoginBannedException extends EskylabLoginException {

    public EskylabLoginBannedException(String message) {
        super(message);
    }
}
