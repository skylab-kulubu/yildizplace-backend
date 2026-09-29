package com.weblab.rplace.weblab.rplace;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The e-skylab login over HTTP, as a browser runs it: the backend's login endpoint
 * sends it to Keycloak (FakeEskylab), the person logs in there, Keycloak sends it
 * back to the backend's callback with the cookie the login endpoint set.
 */
final class EskylabLogin {

	static final String BROWSER_COOKIE = "__Host-place_eskylab";

	private EskylabLogin() {
	}

	/** Where the login endpoint sent the browser, and the cookie it set there. */
	record Started(URI authorizationRequest, Cookie browser, MockHttpServletResponse response) {

		Map<String, String> parameters() {
			return queryOf(authorizationRequest);
		}
	}

	/** GET /api/auth/eskylab/login with the given query parameters (name, value, ...). */
	static Started start(MockMvc mockMvc, String... parameters) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/auth/eskylab/login");
		for (int i = 0; i < parameters.length; i += 2) {
			request.param(parameters[i], parameters[i + 1]);
		}
		MockHttpServletResponse response = mockMvc.perform(request)
				.andExpect(status().isFound())
				.andReturn().getResponse();
		return new Started(URI.create(response.getRedirectedUrl()), response.getCookie(BROWSER_COOKIE), response);
	}

	/** The browser follows Keycloak's redirect back to the backend, carrying the given cookie (or none). */
	static ResultActions returnFromKeycloak(MockMvc mockMvc, URI keycloakRedirect, Cookie browser) throws Exception {
		MockHttpServletRequestBuilder request = get(keycloakRedirect);
		if (browser != null) {
			request.cookie(browser);
		}
		return mockMvc.perform(request);
	}

	/** A whole login in one browser; returns the backend's answer to Keycloak's redirect. */
	static ResultActions logIn(MockMvc mockMvc, Map<String, Object> idTokenClaims, String... loginParameters) throws Exception {
		Started started = start(mockMvc, loginParameters);
		URI back = FakeEskylab.logIn(started.authorizationRequest(), idTokenClaims);
		return returnFromKeycloak(mockMvc, back, started.browser());
	}

	static Map<String, String> queryOf(URI uri) {
		return UriComponentsBuilder.fromUri(uri).build(true).getQueryParams().toSingleValueMap().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == null ? "" : URLDecoder.decode(e.getValue(), UTF_8)));
	}

}
