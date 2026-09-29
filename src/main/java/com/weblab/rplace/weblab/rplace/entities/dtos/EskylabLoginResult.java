package com.weblab.rplace.weblab.rplace.entities.dtos;

import com.weblab.rplace.weblab.rplace.entities.Role;
import com.weblab.rplace.weblab.rplace.entities.User;

import java.net.URI;

/**
 * How an e-skylab login ended: the user to open a Place session for (null when
 * nobody logged in), the role that session gets from the person's Place client
 * roles (ROLE_USER without one), and where on the frontend the browser goes now.
 */
public record EskylabLoginResult(User user, Role role, URI redirect) {
}
