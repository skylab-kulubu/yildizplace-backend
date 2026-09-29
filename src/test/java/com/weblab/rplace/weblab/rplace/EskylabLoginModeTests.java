package com.weblab.rplace.weblab.rplace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.weblab.rplace.weblab.rplace.EskylabLogin.logIn;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.FRONTEND;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * The e-skylab login is open in every login mode (ADR 0060): in eskylab and both
 * for everyone, and in mail mode too, where admins and moderators use it for /admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class EskylabLoginModeTests {

	@DynamicPropertySource
	static void eskylab(DynamicPropertyRegistry registry) {
		FakeEskylab.configureBackend(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void inMailModeTheAdminLoginWithEskylabWorks() throws Exception {
		mockMvc.perform(get("/api/auth/mode")).andExpect(content().json("{\"mode\":\"mail\",\"adminLogin\":\"eskylab\"}", true));

		logIn(mockMvc, claims("school_email", "admin.kisi@std.yildiz.edu.tr"), "returnTo", "/admin")
				.andExpect(redirectedUrl(FRONTEND + "/admin"))
				.andExpect(cookie().exists("user_token"));
	}

	@Nested
	@TestPropertySource(properties = "place.login.mode=eskylab")
	class InEskylabMode {

		@Test
		void theEskylabLoginWorks() throws Exception {
			logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr"))
					.andExpect(redirectedUrl(FRONTEND + "/"))
					.andExpect(cookie().exists("user_token"));
		}
	}

	@Nested
	@TestPropertySource(properties = "place.login.mode=both")
	class InBothMode {

		@Test
		void theEskylabLoginWorks() throws Exception {
			logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr"))
					.andExpect(redirectedUrl(FRONTEND + "/"))
					.andExpect(cookie().exists("user_token"));
		}
	}

}
