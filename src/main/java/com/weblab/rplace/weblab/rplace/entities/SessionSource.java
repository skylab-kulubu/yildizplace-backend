package com.weblab.rplace.weblab.rplace.entities;

/**
 * How a Place session was opened. Sessions from before this was recorded have no
 * source: they came from the mail login.
 */
public enum SessionSource {

    // A link mailed to the school address.
    MAIL,

    // e-skylab (Keycloak).
    ESKYLAB
}
