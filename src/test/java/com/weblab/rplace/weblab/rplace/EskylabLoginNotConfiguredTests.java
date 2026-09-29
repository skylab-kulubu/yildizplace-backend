package com.weblab.rplace.weblab.rplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without KEYCLOAK_CLIENT_SECRET the backend still starts (in mail mode nothing
 * else needs it) and the e-skylab endpoints say that the login is not set up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class EskylabLoginNotConfiguredTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theLoginEndpointSaysTheLoginIsNotSetUp() throws Exception {
		mockMvc.perform(get("/api/auth/eskylab/login"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(cookie().doesNotExist(EskylabLogin.BROWSER_COOKIE));
	}

	@Test
	void theCallbackSaysTheLoginIsNotSetUp() throws Exception {
		mockMvc.perform(get("/api/auth/eskylab/callback").param("code", "a-code").param("state", "a-state"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(cookie().doesNotExist("user_token"));
	}

}
