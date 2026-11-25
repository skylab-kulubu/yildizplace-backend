package com.weblab.rplace.weblab.rplace;

import com.weblab.rplace.weblab.rplace.core.security.PlaceSessions;
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

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API moves from api.place.yildizskylab.com to place-api.yildizskylab.com (ADR 0090).
 * The frontend's middleware reads the login cookies on place.yildizskylab.com, so they move
 * from Domain=place.yildizskylab.com to Domain=yildizskylab.com. While the frontend still
 * calls the old host, a live session there gets its cookies on the new Domain and the old
 * ones deleted (PLACE_LEGACY_COOKIE_DOMAIN).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {"domain=yildizskylab.com", "place.legacy-cookie-domain=place.yildizskylab.com"})
class LegacyCookieDomainTests {

	private static final String OLD_HOST = "https://api.place.yildizskylab.com";

	private static final String NEW_HOST = "https://place-api.yildizskylab.com";

	@DynamicPropertySource
	static void eskylab(DynamicPropertyRegistry registry) {
		FakeEskylab.configureBackend(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestClock clock;

	@Test
	void aSessionOnTheOldHostGetsItsCookiesOnTheNewDomain() throws Exception {
		Cookie session = login("tasima.kisi@std.yildiz.edu.tr");

		List<String> cookies = setCookies(OLD_HOST + "/api/pixels/getColors", session);

		assertThat(cookies).anySatisfy(c -> assertThat(c)
				.startsWith("user_token=" + session.getValue() + ";")
				.contains("Domain=yildizskylab.com").contains("HttpOnly").contains("Secure").contains("SameSite=Lax")
				.doesNotContain("Max-Age=0"));
		assertThat(cookies).anySatisfy(c -> assertThat(c)
				.startsWith("user_token=;").contains("Domain=place.yildizskylab.com").contains("Max-Age=0"));
		assertThat(cookies).anySatisfy(c -> assertThat(c)
				.startsWith("isAdmin=;").contains("Domain=place.yildizskylab.com").contains("Max-Age=0"));
		assertThat(cookies).noneSatisfy(c -> assertThat(c).startsWith("isAdmin=true"));
	}

	@Test
	void anElevatedSessionKeepsOnlyTheTimeItHasLeft() throws Exception {
		Cookie session = EskylabLogin.logIn(mockMvc, claims("school_email", "tasima.yetkili@std.yildiz.edu.tr",
						"resource_access", placeClientRoles("place:admin")))
				.andReturn().getResponse().getCookie("user_token");
		clock.advance(Duration.ofHours(1));

		List<String> cookies = setCookies(OLD_HOST + "/api/pixels/getColors", session);

		assertThat(cookies).anySatisfy(c -> assertThat(c)
				.startsWith("user_token=" + session.getValue() + ";").contains("Domain=yildizskylab.com").contains("Max-Age=25200"));
		assertThat(cookies).anySatisfy(c -> assertThat(c)
				.startsWith("isAdmin=true;").contains("Domain=yildizskylab.com").contains("Max-Age=25200"));
	}

	@Test
	void theNewHostSetsNoCookies() throws Exception {
		Cookie session = login("tasima.yeni@std.yildiz.edu.tr");

		assertThat(setCookies(NEW_HOST + "/api/pixels/getColors", session)).isEmpty();
	}

	@Test
	void aRequestWithoutASessionSetsNoCookies() throws Exception {
		assertThat(setCookies(OLD_HOST + "/api/pixels/getColors", null)).isEmpty();
		assertThat(setCookies(OLD_HOST + "/api/pixels/getColors", new Cookie("user_token", "bilinmeyen"))).isEmpty();
	}

	@Test
	void logoutOnTheOldHostDeletesBothDomains() throws Exception {
		Cookie session = login("tasima.cikis@std.yildiz.edu.tr");

		List<String> cookies = setCookies(OLD_HOST + "/api/users/logout", session);

		// The browser applies the headers in order: the last one for a name and Domain wins.
		for (String name : new String[] {"user_token", "isAdmin"}) {
			for (String domain : new String[] {"yildizskylab.com", "place.yildizskylab.com"}) {
				String last = cookies.stream()
						.filter(c -> c.startsWith(name + "=") && c.contains("Domain=" + domain + ";"))
						.reduce((first, second) -> second).orElse("");
				assertThat(last).startsWith(name + "=;").contains("Max-Age=0");
			}
		}
	}

	@Test
	void theOldDomainMustSitUnderTheNewOne() {
		assertThatThrownBy(() -> new PlaceSessions(null, Clock.systemUTC(), "place.yildizskylab.com", Duration.ofHours(8),
				"place.yildizskylab.com")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new PlaceSessions(null, Clock.systemUTC(), "yildizskylab.com", Duration.ofHours(8),
				"place.example.com")).isInstanceOf(IllegalStateException.class);
		new PlaceSessions(null, Clock.systemUTC(), "yildizskylab.com", Duration.ofHours(8), "");
	}

	private Cookie login(String schoolEmail) throws Exception {
		return EskylabLogin.logIn(mockMvc, claims("school_email", schoolEmail))
				.andReturn().getResponse().getCookie("user_token");
	}

	private List<String> setCookies(String url, Cookie session) throws Exception {
		var request = get(url);
		if (session != null) {
			request.cookie(session);
		}
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getHeaders("Set-Cookie");
	}

}
