package com.weblab.rplace.weblab.rplace.entities;

/**
 * What a user_tokens row is. Rows written before this column existed have no
 * kind; they are the sessions of people who logged in then, so they count as
 * SESSION and never as LINK.
 */
public enum UserTokenKind {

    // A login link mailed to the school address; logs in once.
    LINK,

    // The value of a user_token cookie.
    SESSION
}
