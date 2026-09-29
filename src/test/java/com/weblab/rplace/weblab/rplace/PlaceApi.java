package com.weblab.rplace.weblab.rplace;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Calls to the rest of Place's API that tests use to see whether a session works. */
final class PlaceApi {

	private PlaceApi() {
	}

	/**
	 * Placing a pixel needs a logged-in user: 403 without one. With a session the
	 * request gets through (and is then refused for its made-up Turnstile token).
	 */
	static ResultActions placeAPixelWith(MockMvc mockMvc, Cookie userToken) throws Exception {
		return mockMvc.perform(post("/api/pixels/addProtectedPixel")
				.cookie(userToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"color\":\"#ffffff\",\"number\":0,\"token\":\"not-a-turnstile-token\"}"));
	}

}
