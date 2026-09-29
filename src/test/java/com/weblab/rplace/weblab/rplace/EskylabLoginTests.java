package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.weblab.rplace.weblab.rplace.EskylabLogin.logIn;
import static com.weblab.rplace.weblab.rplace.EskylabLogin.returnFromKeycloak;
import static com.weblab.rplace.weblab.rplace.EskylabLogin.start;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.FRONTEND;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static com.weblab.rplace.weblab.rplace.PlaceApi.placeAPixelWith;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logging in with e-skylab: the backend is Keycloak's confidential client "place"
 * (authorization code + PKCE) and ends the login with the same user_token cookie
 * as the mail login. The backend here runs in mail mode, the default;
 * EskylabLoginModeTests covers the other modes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
class EskylabLoginTests {

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

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void loggingInSendsTheBrowserToKeycloakWithStateNonceAndPkce() throws Exception {
		var login = start(mockMvc);

		assertThat(login.authorizationRequest().toString()).startsWith(FakeEskylab.issuer() + "/authorize?");
		assertThat(login.parameters())
				.containsEntry("response_type", "code")
				.containsEntry("client_id", "place")
				.containsEntry("redirect_uri", FakeEskylab.REDIRECT_URI)
				.containsEntry("code_challenge_method", "S256")
				.containsKeys("state", "nonce")
				.doesNotContainKey("prompt");
		assertThat(login.parameters().get("scope").split(" ")).contains("openid");
		assertThat(login.parameters().get("code_challenge")).matches("[A-Za-z0-9_-]{43}");
		assertThat(login.browser().isHttpOnly()).isTrue();
		assertThat(login.browser().getSecure()).isTrue();
	}

	@Test
	void aLoginEndsOnTheFrontendWithAPlaceSession() throws Exception {
		Cookie session = logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl(FRONTEND + "/"))
				.andExpect(cookie().path("user_token", "/"))
				.andExpect(cookie().domain("user_token", "localhost"))
				.andExpect(cookie().maxAge("user_token", 31536000))
				.andExpect(cookie().httpOnly("user_token", true))
				.andExpect(cookie().secure("user_token", true))
				.andReturn().getResponse().getCookie("user_token");

		placeAPixelWith(mockMvc, session).andExpect(status().isOk());
	}

	@Test
	void keycloaksTokensNeverReachTheBrowser() throws Exception {
		MockHttpServletResponse response = logIn(mockMvc, claims("school_email", "can.ozturk@std.yildiz.edu.tr"))
				.andReturn().getResponse();

		assertThat(response.getContentAsString()).isEmpty();
		// Only Place's own cookies: the session, and the isAdmin cookie deleted (this login has no Place role).
		assertThat(response.getCookies()).extracting(Cookie::getName).containsOnly("user_token", "isAdmin");
		for (String header : response.getHeaderNames()) {
			assertThat(response.getHeaders(header)).noneMatch(value -> value.contains("eyJ"));
		}
	}

	@Test
	void theCodeIsRedeemedWithTheClientSecretAndThePkceVerifier() throws Exception {
		logIn(mockMvc, claims("school_email", "ece.kurt@std.yildiz.edu.tr")).andExpect(cookie().exists("user_token"));

		List<FakeEskylab.Request> redemptions = FakeEskylab.requests().stream()
				.filter(request -> request.path().equals("/e-skylab/token"))
				.toList();
		FakeEskylab.Request redemption = redemptions.get(redemptions.size() - 1);
		assertThat(redemption.authorization())
				.isEqualTo("Basic " + Base64.getEncoder().encodeToString("place:test-client-secret".getBytes(UTF_8)));
		assertThat(redemption.body()).contains("grant_type=authorization_code").contains("code_verifier=");
	}

	@Test
	void keycloaksDiscoveryDocumentAndKeysAreReadOnce() throws Exception {
		logIn(mockMvc, claims("school_email", "deniz.ak@std.yildiz.edu.tr")).andExpect(cookie().exists("user_token"));
		int before = FakeEskylab.requests().size();

		logIn(mockMvc, claims("school_email", "deniz.ak@std.yildiz.edu.tr")).andExpect(cookie().exists("user_token"));

		assertThat(FakeEskylab.requests().subList(before, FakeEskylab.requests().size()))
				.extracting(FakeEskylab.Request::path)
				.containsExactly("/e-skylab/authorize", "/e-skylab/token");
	}

	@Test
	void mailAndEskylabLoginsWithTheSameSchoolMailShareOneAccount() throws Exception {
		String link = MailLogin.requestLink(mockMvc, smtp, "zeynep.demir@std.yildiz.edu.tr");
		openLink(mockMvc, link).andExpect(cookie().exists("user_token"));

		logIn(mockMvc, claims("school_email", "Zeynep.Demir@STD.yildiz.edu.tr")).andExpect(cookie().exists("user_token"));

		assertThat(accountsOf("zeynep.demir@std.yildiz.edu.tr")).isEqualTo(1);
		assertThat(accountsOf("Zeynep.Demir@STD.yildiz.edu.tr")).isZero();
	}

	@Test
	void aFirstEskylabLoginOpensAnAccountWithTheUserRole() throws Exception {
		logIn(mockMvc, claims("school_email", "yeni.uye@std.yildiz.edu.tr")).andExpect(cookie().exists("user_token"));

		assertThat(database.queryForList("""
				SELECT a.role FROM users u JOIN authorities a ON a.user_id = u.id WHERE u.school_mail = ?""",
				String.class, "yeni.uye@std.yildiz.edu.tr")).containsExactly("ROLE_USER");
	}

	@Test
	void anIdTokenWithoutASchoolEmailIsRefused() throws Exception {
		assertRefused(logIn(mockMvc, claims("email", "kisisel@gmail.com")));
		assertThat(accountsOf("kisisel@gmail.com")).isZero();
	}

	@Test
	void aSchoolEmailThatIsNotASchoolAddressIsRefused() throws Exception {
		assertRefused(logIn(mockMvc, claims("school_email", "kisisel@gmail.com")));
		assertThat(accountsOf("kisisel@gmail.com")).isZero();
	}

	@Test
	void aBannedUserIsRefused() throws Exception {
		logIn(mockMvc, claims("school_email", "yasakli.kisi@std.yildiz.edu.tr")).andExpect(cookie().exists("user_token"));
		database.update("""
				INSERT INTO banned_users (banned_user_id, reason, banned_at)
				SELECT id, 'test', now() FROM users WHERE school_mail = ?""", "yasakli.kisi@std.yildiz.edu.tr");

		assertRefused(logIn(mockMvc, claims("school_email", "yasakli.kisi@std.yildiz.edu.tr")));
	}

	@Test
	void anIdTokenForAnotherClientIsRefused() throws Exception {
		assertRefused(logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr", "aud", List.of("another-client"))));
	}

	@Test
	void anIdTokenFromAnotherIssuerIsRefused() throws Exception {
		assertRefused(logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr", "iss", "https://evil.example/realms/e-skylab")));
	}

	@Test
	void anExpiredIdTokenIsRefused() throws Exception {
		long now = Instant.now().getEpochSecond();
		assertRefused(logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr", "iat", now - 7200, "exp", now - 3600)));
	}

	@Test
	void anIdTokenIssuedInTheFutureIsRefused() throws Exception {
		long now = Instant.now().getEpochSecond();
		assertRefused(logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr", "iat", now + 3600, "nbf", now, "exp", now + 7200)));
	}

	@Test
	void anIdTokenMadeForAnotherLoginIsRefused() throws Exception {
		assertRefused(logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr", "nonce", "another-login")));
	}

	@Test
	void anUnknownStateIsRefused() throws Exception {
		var login = start(mockMvc);
		URI back = FakeEskylab.logIn(login.authorizationRequest(), claims("school_email", "ali.kaya@std.yildiz.edu.tr"));

		assertRefused(returnFromKeycloak(mockMvc, withParameter(back, "state", "made-up-state"), login.browser()));
	}

	@Test
	void aStateWorksOnlyOnce() throws Exception {
		var login = start(mockMvc);
		var claims = claims("school_email", "ali.kaya@std.yildiz.edu.tr");
		URI back = FakeEskylab.logIn(login.authorizationRequest(), claims);
		returnFromKeycloak(mockMvc, back, login.browser()).andExpect(cookie().exists("user_token"));

		// The same authorization request again: Keycloak hands out a fresh code for the used state.
		URI replay = FakeEskylab.logIn(login.authorizationRequest(), claims);
		assertRefused(returnFromKeycloak(mockMvc, replay, login.browser()));
	}

	@Test
	void aLoginFinishesOnlyInTheBrowserThatStartedIt() throws Exception {
		var attacker = start(mockMvc);
		URI back = FakeEskylab.logIn(attacker.authorizationRequest(), claims("school_email", "saldirgan@std.yildiz.edu.tr"));
		Cookie victim = start(mockMvc).browser();

		assertRefused(returnFromKeycloak(mockMvc, back, victim));
	}

	@Test
	void aLoginFinishesOnlyInABrowserWithTheCookie() throws Exception {
		var login = start(mockMvc);
		URI back = FakeEskylab.logIn(login.authorizationRequest(), claims("school_email", "ali.kaya@std.yildiz.edu.tr"));

		assertRefused(returnFromKeycloak(mockMvc, back, null));
	}

	@Test
	void aLoginLeftForMoreThanTenMinutesIsRefused() throws Exception {
		var login = start(mockMvc);
		URI back = FakeEskylab.logIn(login.authorizationRequest(), claims("school_email", "ali.kaya@std.yildiz.edu.tr"));
		clock.advance(Duration.ofMinutes(11));

		assertRefused(returnFromKeycloak(mockMvc, back, login.browser()));
	}

	@Test
	void theLoginReturnsToTheRequestedPageOfTheFrontend() throws Exception {
		logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr"), "returnTo", "/play?x=10&y=20")
				.andExpect(redirectedUrl(FRONTEND + "/play?x=10&y=20"))
				.andExpect(cookie().exists("user_token"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://evil.example/", "//evil.example/", "/\\evil.example/", "javascript:alert(1)", "play", "/play\r\nSet-Cookie: x=y"})
	void aReturnPathOffTheFrontendIsIgnored(String returnTo) throws Exception {
		logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr"), "returnTo", returnTo)
				.andExpect(redirectedUrl(FRONTEND + "/"))
				.andExpect(cookie().exists("user_token"));
	}

	@Test
	void aSilentLoginAsksKeycloakNotToShowAnything() throws Exception {
		assertThat(start(mockMvc, "prompt", "none").parameters()).containsEntry("prompt", "none");
	}

	@Test
	void aSilentLoginWithAnEskylabSessionLogsIn() throws Exception {
		logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr"), "prompt", "none")
				.andExpect(redirectedUrl(FRONTEND + "/"))
				.andExpect(cookie().exists("user_token"));
	}

	@Test
	void aSilentLoginWithoutAnEskylabSessionReturnsToTheFrontendLoggedOut() throws Exception {
		var login = start(mockMvc, "prompt", "none", "returnTo", "/play");
		// Keycloak's answer to prompt=none when the browser has no e-skylab session.
		URI back = UriComponentsBuilder.fromUriString(FakeEskylab.REDIRECT_URI)
				.queryParam("error", "login_required")
				.queryParam("state", login.parameters().get("state"))
				.build().toUri();

		returnFromKeycloak(mockMvc, back, login.browser())
				.andExpect(status().isFound())
				.andExpect(redirectedUrl(FRONTEND + "/play?sso=none"))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	@Test
	void loggingOutEndsOnlyThePlaceSession() throws Exception {
		Cookie session = logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr"))
				.andReturn().getResponse().getCookie("user_token");
		int keycloakRequests = FakeEskylab.requests().size();

		mockMvc.perform(get("/api/users/logout").cookie(session))
				.andExpect(status().isOk())
				.andExpect(cookie().maxAge("user_token", 0))
				.andExpect(cookie().maxAge("isAdmin", 0));

		placeAPixelWith(mockMvc, session).andExpect(status().isForbidden());
		assertThat(FakeEskylab.requests()).hasSize(keycloakRequests);
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void noTokenOrSecretReachesTheLog(CapturedOutput log) throws Exception {
		var login = start(mockMvc);
		URI back = FakeEskylab.logIn(login.authorizationRequest(), claims("school_email", "ali.kaya@std.yildiz.edu.tr"));
		returnFromKeycloak(mockMvc, back, login.browser()).andExpect(cookie().exists("user_token"));
		assertRefused(logIn(mockMvc, claims("school_email", "ali.kaya@std.yildiz.edu.tr", "aud", List.of("another-client"))));

		assertThat(log.getAll())
				.contains("e-skylab login refused")
				.doesNotContain("eyJ")
				.doesNotContain(FakeEskylab.CLIENT_SECRET)
				.doesNotContain(EskylabLogin.queryOf(back).get("code"));
	}

	/** Refused: back on the frontend with ?sso=error, and not logged in. */
	private static void assertRefused(ResultActions callback) throws Exception {
		callback.andExpect(status().isFound())
				.andExpect(redirectedUrl(FRONTEND + "/?sso=error"))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	private int accountsOf(String schoolMail) {
		return database.queryForObject("SELECT count(*) FROM users WHERE school_mail = ?", Integer.class, schoolMail);
	}

	private static URI withParameter(URI uri, String name, String value) {
		Map<String, String> parameters = EskylabLogin.queryOf(uri);
		var changed = UriComponentsBuilder.fromUri(uri).replaceQuery(null);
		parameters.forEach((key, current) -> changed.queryParam(key, key.equals(name) ? value : current));
		return changed.build().toUri();
	}

}
