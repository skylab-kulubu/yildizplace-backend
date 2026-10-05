package com.weblab.rplace.weblab.rplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The frontend's dev server (http://localhost:3000) is an allowed origin only with
 * the dev profile (SPRING_PROFILES_ACTIVE=dev); production allows the https
 * frontend alone (CookieAndCsrfTests, WebSocketOriginTests).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles({"test", "dev"})
class DevProfileCorsTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theDevProfileAllowsTheLocalFrontend() throws Exception {
		for (String origin : new String[] {"http://localhost:3000", "https://place.yildizskylab.com"}) {
			mockMvc.perform(options("/api/users/register")
							.header("Origin", origin)
							.header("Access-Control-Request-Method", "POST"))
					.andExpect(status().isOk())
					.andExpect(header().string("Access-Control-Allow-Origin", origin));
		}
	}

	@Test
	void theDevProfileLetsTheLocalFrontendOpenTheCanvasSocket() throws Exception {
		// SockJS's /info answers only allowed origins; the handshake uses the same list (WebSocketConfig).
		for (String origin : new String[] {"http://localhost:3000", "https://place.yildizskylab.com"}) {
			mockMvc.perform(get("/rplace/info").header("Origin", origin))
					.andExpect(status().isOk())
					.andExpect(header().string("Access-Control-Allow-Origin", origin));
		}
		mockMvc.perform(get("/rplace/info").header("Origin", "https://evil.example"))
				.andExpect(status().isForbidden());
	}

}
