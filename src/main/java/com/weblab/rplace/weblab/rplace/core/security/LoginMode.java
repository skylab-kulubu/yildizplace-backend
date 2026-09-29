package com.weblab.rplace.weblab.rplace.core.security;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * How people log in to Place, chosen with PLACE_LOGIN_MODE (ADR 0060):
 * with a link mailed to the school address, with e-skylab, or with both.
 */
public enum LoginMode {

    MAIL("mail"),
    ESKYLAB("eskylab"),
    BOTH("both");

    private final String value;

    LoginMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isMailLoginOpen() {
        return this != ESKYLAB;
    }

    public static LoginMode fromValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "PLACE_LOGIN_MODE must be one of " + Arrays.stream(values()).map(LoginMode::getValue).collect(Collectors.joining(", "))
                                + "; got '" + value + "'"));
    }
}
