package com.weblab.rplace.weblab.rplace;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A request as it reaches Place in production: from Traefik's address on the
 * Dokploy overlay network (inside the default PLACE_TRUSTED_PROXY_RANGES,
 * 10.0.1.0/24), carrying the X-Forwarded-For chain Traefik passed on.
 */
final class TraefikProxy {

	static final String ADDRESS = "10.0.1.4";

	private TraefikProxy() {
	}

	/** Through Traefik, with this X-Forwarded-For chain (its right end is what Traefik saw). */
	static RequestPostProcessor forwarding(String forwardedFor) {
		return request -> {
			request.setRemoteAddr(ADDRESS);
			request.addHeader("X-Forwarded-For", forwardedFor);
			return request;
		};
	}

	/** Straight from this address, not through Traefik, with whatever X-Forwarded-For it chose to send. */
	static RequestPostProcessor directFrom(String peer, String forwardedFor) {
		return request -> {
			request.setRemoteAddr(peer);
			request.addHeader("X-Forwarded-For", forwardedFor);
			return request;
		};
	}

}
