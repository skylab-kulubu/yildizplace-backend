package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Place's login cookies are SameSite=Lax, Secure and HttpOnly however they are set
 * or deleted (ticket 08). Lax, not Strict: the frontend's middleware reads them on
 * top-level navigations, also from a link in a mail client on another site.
 *
 * <p>With Lax a page on another site cannot make the browser send them with a POST;
 * a page on a sibling subdomain (same site, so SameSite does not help) is stopped by
 * the CORS check on its Origin before the endpoint runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
class CookieAndCsrfTests {

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

	@Test
	void theMailLoginSetsLaxSecureHttpOnlyCookies() throws Exception {
		String link = MailLogin.requestLink(mockMvc, smtp, "cerez.mail@std.yildiz.edu.tr");

		assertLoginCookie(openLink(mockMvc, link), "user_token");
		assertLoginCookie(openLink(mockMvc, link), "isAdmin");
	}

	@Test
	void anElevatedEskylabLoginSetsLaxSecureHttpOnlyCookies() throws Exception {
		ResultActions login = EskylabLogin.logIn(mockMvc, claims("school_email", "cerez.yetkili@std.yildiz.edu.tr",
				"resource_access", placeClientRoles("place:admin")));

		assertLoginCookie(login, "user_token");
		assertLoginCookie(login, "isAdmin")
				.andExpect(cookie().value("isAdmin", "true"));
	}

	@Test
	void logoutDeletesTheCookiesWithTheSameAttributes() throws Exception {
		Cookie session = EskylabLogin.logIn(mockMvc, claims("school_email", "cerez.cikis@std.yildiz.edu.tr"))
				.andReturn().getResponse().getCookie("user_token");

		ResultActions logout = mockMvc.perform(get("/api/users/logout").cookie(session));

		assertLoginCookie(logout, "user_token").andExpect(cookie().maxAge("user_token", 0));
		assertLoginCookie(logout, "isAdmin").andExpect(cookie().maxAge("isAdmin", 0));
	}

	@Test
	void aModeratorRequestFromAPageOnAnotherOriginDoesNothing() throws Exception {
		Cookie moderator = moderatorSession("csrf.moderator@std.yildiz.edu.tr");
		String target = "csrf.hedef@std.yildiz.edu.tr";
		EskylabLogin.logIn(mockMvc, claims("school_email", target));

		mockMvc.perform(post("/api/bans/banUser").cookie(moderator)
						.header("Origin", "https://evil.yildizskylab.com")
						.param("schoolMail", target).param("reason", "csrf"))
				.andExpect(status().isForbidden());

		assertThat(bansOf(target)).isZero();
	}

	@Test
	void aModeratorRequestFromPlacesFrontendWorks() throws Exception {
		Cookie moderator = moderatorSession("cors.moderator@std.yildiz.edu.tr");
		String target = "cors.hedef@std.yildiz.edu.tr";
		EskylabLogin.logIn(mockMvc, claims("school_email", target));

		mockMvc.perform(post("/api/bans/banUser").cookie(moderator)
						.header("Origin", "https://place.yildizskylab.com")
						.param("schoolMail", target).param("reason", "test"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		assertThat(bansOf(target)).isEqualTo(1);
	}

	private static ResultActions assertLoginCookie(ResultActions response, String name) throws Exception {
		return response
				.andExpect(cookie().exists(name))
				.andExpect(cookie().sameSite(name, "Lax"))
				.andExpect(cookie().secure(name, true))
				.andExpect(cookie().httpOnly(name, true))
				.andExpect(cookie().path(name, "/"))
				.andExpect(cookie().domain(name, "localhost"));
	}

	private Cookie moderatorSession(String schoolMail) throws Exception {
		return EskylabLogin.logIn(mockMvc, claims("school_email", schoolMail, "resource_access", placeClientRoles("place:moderator")))
				.andReturn().getResponse().getCookie("user_token");
	}

	private int bansOf(String schoolMail) {
		return database.queryForObject(
				"SELECT count(*) FROM banned_users b JOIN users u ON u.id = b.banned_user_id WHERE u.school_mail = ?", Integer.class, schoolMail);
	}

}
