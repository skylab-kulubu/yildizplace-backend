package com.weblab.rplace.weblab.rplace.core.security.eskylab;

/**
 * An e-skylab login that cannot go on. The message is for the log: it says what
 * went wrong and never carries a token, a code or the client secret.
 */
public class EskylabLoginException extends Exception {

    public EskylabLoginException(String message) {
        super(message);
    }
}
