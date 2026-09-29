package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.weblab.rplace.weblab.rplace.core.utilities.turnstile.TurnstileService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static com.weblab.rplace.weblab.rplace.PlaceApi.placeAPixelWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin and moderator rights come only from the Keycloak client roles of "place"
 * (place:admin, place:moderator), only on sessions opened with e-skylab, and last
 * as long as such a session: PLACE_ELEVATED_SESSION_TTL, 8 hours by default
 * (ADR 0060). The authorities table in Place's database no longer grants anything.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
class PlaceRolesTests {

	@RegisterExtension
	static final GreenMailExtension smtp = MailLogin.smtpServer();

	@DynamicPropertySource
	static void services(DynamicPropertyRegistry registry) {
		MailLogin.sendMailThrough(smtp, registry);
		FakeEskylab.configureBackend(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate database;

	@Autowired
	private TestClock clock;

	// Cloudflare, which checks the Turnstile answer before a session may place pixels.
	@MockBean
	private TurnstileService turnstile;

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@ParameterizedTest
	@ValueSource(strings = {"place:admin", "place:moderator"})
	void anEskylabLoginWithAPlaceRoleOpensTheModeratorEndpoints(String placeRole) throws Exception {
		Cookie session = eskylabSession("yetkili.kisi@std.yildiz.edu.tr", placeRole);

		moderate(session).andExpect(status().isOk());
	}

	@ParameterizedTest
	@MethodSource("tokensWithoutAPlaceRole")
	void anEskylabLoginWithoutAPlaceRoleIsOnlyAUser(Map<String, Object> roleClaims) throws Exception {
		Map<String, Object> idToken = new HashMap<>(roleClaims);
		idToken.put("school_email", "rolsuz.kisi@std.yildiz.edu.tr");
		Cookie session = EskylabLogin.logIn(mockMvc, idToken).andReturn().getResponse().getCookie("user_token");

		moderate(session).andExpect(status().isForbidden());
		placeAPixelWith(mockMvc, session).andExpect(status().isOk());
	}

	static Stream<Map<String, Object>> tokensWithoutAPlaceRole() {
		return Stream.of(
				Map.of(),
				Map.of("resource_access", placeClientRoles()),
				Map.of("resource_access", placeClientRoles("place:owner", "PLACE:ADMIN", "admin")),
				Map.of("resource_access", Map.of("core", Map.of("roles", List.of("place:admin", "place:moderator")))),
				Map.of("realm_access", Map.of("roles", List.of("place:admin", "place:moderator"))),
				Map.of("roles", List.of("place:admin")),
				Map.of("resource_access", Map.of("place", Map.of("roles", "place:admin"))));
	}

	@Test
	void anAdminInPlacesDatabaseLoggingInWithEskylabWithoutAPlaceRoleIsOnlyAUser() throws Exception {
		String schoolMail = "db.admin@std.yildiz.edu.tr";
		eskylabSession(schoolMail);
		grantInDatabase(schoolMail, "ROLE_ADMIN", "ROLE_MODERATOR");

		moderate(eskylabSession(schoolMail)).andExpect(status().isForbidden());
	}

	@Test
	void anAdminsSessionFromBeforeTheUpgradeIsOnlyAUser() throws Exception {
		String schoolMail = "eski.oturum@std.yildiz.edu.tr";
		MailLogin.requestLink(mockMvc, smtp, schoolMail);
		grantInDatabase(schoolMail, "ROLE_ADMIN");
		// A session as the version before login links and sessions were told apart wrote it: no kind.
		database.update("""
				INSERT INTO user_tokens (user_id, token, created_at, user_ip, is_used, used_at)
				SELECT id, 'pre-upgrade-admin-session', now(), '127.0.0.1', true, now() FROM users WHERE school_mail = ?""", schoolMail);
		Cookie session = new Cookie("user_token", "pre-upgrade-admin-session");

		moderate(session).andExpect(status().isForbidden());
		placeAPixelWith(mockMvc, session).andExpect(status().isOk());
	}

	@Test
	void anAdminInPlacesDatabaseLoggingInByMailIsOnlyAUser() throws Exception {
		String schoolMail = "eski.admin@std.yildiz.edu.tr";
		String link = MailLogin.requestLink(mockMvc, smtp, schoolMail);
		grantInDatabase(schoolMail, "ROLE_ADMIN", "ROLE_MODERATOR");

		Cookie session = openLink(mockMvc, link)
				.andExpect(cookie().maxAge("user_token", 31536000))
				.andExpect(cookie().value("isAdmin", ""))
				.andExpect(cookie().maxAge("isAdmin", 0))
				.andReturn().getResponse().getCookie("user_token");

		moderate(session).andExpect(status().isForbidden());
	}

	@Test
	void anElevatedSessionsCookiesLastAsLongAsTheSession() throws Exception {
		EskylabLogin.logIn(mockMvc, claims("school_email", "cerez.admin@std.yildiz.edu.tr", "resource_access", placeClientRoles("place:admin")))
				.andExpect(cookie().maxAge("user_token", 8 * 3600))
				.andExpect(cookie().value("isAdmin", "true"))
				.andExpect(cookie().maxAge("isAdmin", 8 * 3600))
				.andExpect(cookie().path("isAdmin", "/"))
				.andExpect(cookie().domain("isAdmin", "localhost"))
				.andExpect(cookie().httpOnly("isAdmin", true))
				.andExpect(cookie().secure("isAdmin", true));
	}

	@Test
	void aSessionWithoutAPlaceRoleKeepsTheLongCookieAndClearsTheAdminCookie() throws Exception {
		EskylabLogin.logIn(mockMvc, claims("school_email", "cerez.kullanici@std.yildiz.edu.tr"))
				.andExpect(cookie().maxAge("user_token", 31536000))
				.andExpect(cookie().value("isAdmin", ""))
				.andExpect(cookie().maxAge("isAdmin", 0));
	}

	@Test
	void anElevatedSessionEndsAfterEightHours() throws Exception {
		Cookie session = eskylabSession("sekiz.saat@std.yildiz.edu.tr", "place:moderator");

		clock.advance(Duration.ofHours(8).minusSeconds(1));
		moderate(session).andExpect(status().isOk());

		clock.advance(Duration.ofSeconds(1));
		// Ended, not kept on as a user session: the frontend logs in again and gets fresh roles.
		placeAPixelWith(mockMvc, session)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(cookie().maxAge("user_token", 0))
				.andExpect(cookie().maxAge("isAdmin", 0));

		// The session is gone: even with the clock back, the cookie logs nobody in.
		clock.reset();
		moderate(session).andExpect(status().isForbidden());
		placeAPixelWith(mockMvc, session).andExpect(status().isForbidden());
	}

	@Test
	void aSessionWithoutAPlaceRoleOutlivesEightHours() throws Exception {
		Cookie eskylab = eskylabSession("uzun.eskylab@std.yildiz.edu.tr");
		Cookie mail = openLink(mockMvc, MailLogin.requestLink(mockMvc, smtp, "uzun.mail@std.yildiz.edu.tr"))
				.andReturn().getResponse().getCookie("user_token");

		clock.advance(Duration.ofDays(30));

		placeAPixelWith(mockMvc, eskylab).andExpect(status().isOk());
		placeAPixelWith(mockMvc, mail).andExpect(status().isOk());
	}

	@ParameterizedTest
	@CsvSource({
			"POST, /api/pixels/addPixel",
			"GET, /api/pixels/getBoard",
			"GET, /api/pixels/getByXAndY",
			"POST, /api/pixels/fill",
			"POST, /api/pixels/bringBackPixels",
			"GET, /api/pixelLogs/getPixelLogs",
			"GET, /api/userTokens/getAll",
			"GET, /api/userTokens/getUserToken",
			"GET, /api/bans/getBannedUsers",
			"POST, /api/bans/banUser"})
	void theModeratorEndpointsLetInOnlyElevatedSessions(String method, String endpoint) throws Exception {
		Cookie user = eskylabSession("kapi.kullanici@std.yildiz.edu.tr");
		Cookie moderator = eskylabSession("kapi.moderator@std.yildiz.edu.tr", "place:moderator");
		Cookie admin = eskylabSession("kapi.admin@std.yildiz.edu.tr", "place:admin");

		mockMvc.perform(request(HttpMethod.valueOf(method), endpoint).cookie(user)).andExpect(status().isForbidden());
		// Past the gate: whatever the endpoint answers to an empty request, it is not a refusal.
		for (Cookie elevated : List.of(moderator, admin)) {
			int answer = mockMvc.perform(request(HttpMethod.valueOf(method), endpoint).cookie(elevated)).andReturn().getResponse().getStatus();
			assertThat(answer).isNotIn(401, 403);
		}
	}

	@Test
	void anEskylabModeratorPlacesPixelsWithoutTheCooldown() throws Exception {
		Cookie moderator = eskylabSession("hizli.moderator@std.yildiz.edu.tr", "place:moderator");
		passTurnstile(moderator);

		placeAPixelDirectly(moderator, 10, 10).andExpect(jsonPath("$.success").value(true));
		placeAPixelDirectly(moderator, 10, 11).andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void theEndpointThatMadeModeratorsIsGone() throws Exception {
		String schoolMail = "aday.moderator@std.yildiz.edu.tr";
		eskylabSession(schoolMail);
		Cookie admin = eskylabSession("gorevli.admin@std.yildiz.edu.tr", "place:admin");

		mockMvc.perform(post("/api/users/addModerator").param("schoolMail", schoolMail).cookie(admin))
				.andExpect(status().isNotFound());
		assertThat(rolesInDatabase(schoolMail)).containsExactly("ROLE_USER");
	}

	@Test
	void theEndpointThatRemovedModeratorsIsGone() throws Exception {
		String schoolMail = "eski.moderator@std.yildiz.edu.tr";
		eskylabSession(schoolMail);
		grantInDatabase(schoolMail, "ROLE_MODERATOR");
		Cookie admin = eskylabSession("gorevli.admin@std.yildiz.edu.tr", "place:admin");

		mockMvc.perform(post("/api/users/removeModerator").param("schoolMail", schoolMail).cookie(admin))
				.andExpect(status().isNotFound());
		// Left for ticket 05, which moves these roles to Keycloak.
		assertThat(rolesInDatabase(schoolMail)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_MODERATOR");
	}

	/** Logs in with e-skylab, the ID token carrying these client roles of "place". */
	private Cookie eskylabSession(String schoolMail, String... placeRoles) throws Exception {
		return EskylabLogin.logIn(mockMvc, claims("school_email", schoolMail, "resource_access", placeClientRoles(placeRoles)))
				.andReturn().getResponse().getCookie("user_token");
	}

	/** A moderator endpoint: the list of banned users. */
	private ResultActions moderate(Cookie session) throws Exception {
		return mockMvc.perform(get("/api/bans/getBannedUsers").cookie(session));
	}

	/** Cloudflare accepts the session's Turnstile answer, so it may place pixels for a while. */
	private void passTurnstile(Cookie session) throws Exception {
		when(turnstile.verifyToken("turnstile-passed")).thenReturn(true);
		mockMvc.perform(post("/api/userTokens/extendToken").cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"securityToken\":\"turnstile-passed\"}"))
				.andExpect(jsonPath("$.success").value(true));
	}

	/** The admins' and moderators' way to place a pixel: coordinates in the clear, no rate limit. */
	private ResultActions placeAPixelDirectly(Cookie session, int x, int y) throws Exception {
		return mockMvc.perform(post("/api/pixels/addPixel").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"x\":" + x + ",\"y\":" + y + ",\"color\":\"ffffff\"}"));
	}

	private List<String> rolesInDatabase(String schoolMail) {
		return database.queryForList("SELECT a.role FROM users u JOIN authorities a ON a.user_id = u.id WHERE u.school_mail = ?",
				String.class, schoolMail);
	}

	/** Roles as the version before this change kept them: rows in the authorities table. */
	private void grantInDatabase(String schoolMail, String... roles) {
		for (String role : roles) {
			database.update("INSERT INTO authorities (user_id, role) SELECT id, ? FROM users WHERE school_mail = ?", role, schoolMail);
		}
	}

}
