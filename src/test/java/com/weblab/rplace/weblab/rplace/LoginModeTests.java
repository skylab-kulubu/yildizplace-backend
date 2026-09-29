package com.weblab.rplace.weblab.rplace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLACE_LOGIN_MODE (mail, eskylab or both; mail when unset) picks how people log in.
 * The frontend learns it from the public GET /api/auth/mode.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class LoginModeTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void withoutAModeTheBackendRunsInMailMode() throws Exception {
		mockMvc.perform(get("/api/auth/mode"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"mode\":\"mail\",\"adminLogin\":\"eskylab\"}", true));
	}

	@Test
	void anUnknownModeStopsTheApplicationFromStarting() {
		var application = new SpringApplicationBuilder(WeblabRplaceApplication.class, PostgresTestcontainer.class)
				.profiles("test");

		assertThatThrownBy(() -> application.run("--place.login.mode=mial", "--server.port=0"))
				.hasStackTraceContaining("PLACE_LOGIN_MODE")
				.hasStackTraceContaining("mial");
	}

	@Nested
	@TestPropertySource(properties = "place.login.mode=eskylab")
	class InEskylabMode {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void theModeEndpointSaysEskylab() throws Exception {
			mockMvc.perform(get("/api/auth/mode"))
					.andExpect(status().isOk())
					.andExpect(content().json("{\"mode\":\"eskylab\",\"adminLogin\":\"eskylab\"}", true));
		}

		@Test
		void askingForALoginLinkIsRefused() throws Exception {
			mockMvc.perform(post("/api/users/register")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"schoolMail\":\"ayse.yilmaz@std.yildiz.edu.tr\"}"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.message").isNotEmpty());
		}

		@Test
		void openingALoginLinkIsRefused() throws Exception {
			mockMvc.perform(post("/api/users/login").param("token", "any-link"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.message").isNotEmpty())
					.andExpect(cookie().doesNotExist("user_token"));
		}

	}

}
