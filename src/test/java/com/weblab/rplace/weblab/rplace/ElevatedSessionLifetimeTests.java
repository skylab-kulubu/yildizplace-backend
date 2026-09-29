package com.weblab.rplace.weblab.rplace;

import jakarta.servlet.http.Cookie;
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

import java.time.Duration;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLACE_ELEVATED_SESSION_TTL sets how long a session with a Place role lasts;
 * PlaceRolesTests covers the default, 8 hours.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "place.elevated-session-ttl=30m")
class ElevatedSessionLifetimeTests {

	@DynamicPropertySource
	static void eskylab(DynamicPropertyRegistry registry) {
		FakeEskylab.configureBackend(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestClock clock;

	@Test
	void anElevatedSessionLastsAsLongAsTheEnvironmentSays() throws Exception {
		Cookie session = EskylabLogin.logIn(mockMvc, claims("school_email", "yarim.saat@std.yildiz.edu.tr", "resource_access", placeClientRoles("place:admin")))
				.andExpect(cookie().maxAge("user_token", 1800))
				.andExpect(cookie().maxAge("isAdmin", 1800))
				.andReturn().getResponse().getCookie("user_token");

		clock.advance(Duration.ofMinutes(29));
		mockMvc.perform(get("/api/bans/getBannedUsers").cookie(session)).andExpect(status().isOk());

		clock.advance(Duration.ofMinutes(1));
		mockMvc.perform(get("/api/bans/getBannedUsers").cookie(session)).andExpect(status().isUnauthorized());
	}

}
