package com.weblab.rplace.weblab.rplace.entities.dtos;

import com.weblab.rplace.weblab.rplace.entities.User;

import java.net.URI;

/**
 * How an e-skylab login ended: the user to open a Place session for (null when
 * nobody logged in) and where on the frontend the browser goes now.
 */
public record EskylabLoginResult(User user, URI redirect) {
}
