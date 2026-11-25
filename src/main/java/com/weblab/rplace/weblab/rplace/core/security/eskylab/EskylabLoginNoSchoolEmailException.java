package com.weblab.rplace.weblab.rplace.core.security.eskylab;

/**
 * A login refused because the ID token carries no school address: the claim is missing or blank, or the
 * address is outside the school domain. The frontend gets ?sso=no_school_email and can tell the person to
 * link their YTÜ account. The message never carries the address.
 */
public class EskylabLoginNoSchoolEmailException extends EskylabLoginException {

    public EskylabLoginNoSchoolEmailException(String message) {
        super(message);
    }
}
