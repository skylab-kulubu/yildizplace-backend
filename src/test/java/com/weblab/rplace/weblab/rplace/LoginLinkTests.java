package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With PLACE_LOGIN_LINK_SINGLE_USE=true the mailed login link logs in once. Opening
 * it starts a session under a new value in the user_token cookie; the link value
 * itself never is a session. ReusableLoginLinkTests covers the default, false.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "place.login.link-single-use=true")
class LoginLinkTests {

	@RegisterExtension
	static final GreenMailExtension smtp = MailLogin.smtpServer();

	@DynamicPropertySource
	static void mailServer(DynamicPropertyRegistry registry) {
		MailLogin.sendMailThrough(smtp, registry);
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
	void openingTheLinkStartsASessionUnderANewValue() throws Exception {
		String link = requestLink("ali.kaya@std.yildiz.edu.tr");

		Cookie session = openLink(mockMvc, link)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(cookie().path("user_token", "/"))
				.andExpect(cookie().domain("user_token", "localhost"))
				.andExpect(cookie().maxAge("user_token", 31536000))
				.andExpect(cookie().httpOnly("user_token", true))
				.andExpect(cookie().secure("user_token", true))
				.andReturn().getResponse().getCookie("user_token");

		assertThat(session.getValue()).isNotEqualTo(link);
		placeAPixelWith(session).andExpect(status().isOk());
	}

	@Test
	void aLinkLogsInOnlyOnce() throws Exception {
		String link = requestLink("zeynep.demir@std.yildiz.edu.tr");
		openLink(mockMvc, link).andExpect(jsonPath("$.success").value(true));

		openLink(mockMvc, link)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	@Test
	void anUnopenedLinkStopsWorkingAfterItsLifetime() throws Exception {
		String link = requestLink("kaan.aydin@std.yildiz.edu.tr");
		clock.advance(Duration.ofMinutes(61));

		openLink(mockMvc, link)
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	@Test
	void theLinkValueIsNotASession() throws Exception {
		String link = requestLink("mehmet.can@std.yildiz.edu.tr");
		openLink(mockMvc, link).andExpect(jsonPath("$.success").value(true));

		placeAPixelWith(new Cookie("user_token", link)).andExpect(status().isForbidden());
	}

	@Test
	void loggingInDoesNotUseUpTheHourlyAllowanceOfFiveLinks() throws Exception {
		String schoolMail = "elif.sahin@std.yildiz.edu.tr";
		for (int i = 0; i < 3; i++) {
			openLink(mockMvc, requestLink(schoolMail)).andExpect(jsonPath("$.success").value(true));
		}

		requestLink(schoolMail);
	}

	@Test
	void aSessionFromBeforeTheUpgradeKeepsWorking() throws Exception {
		String session = rowFromBeforeTheUpgrade("burak.yildiz@std.yildiz.edu.tr", "pre-upgrade-session", true);

		placeAPixelWith(new Cookie("user_token", session)).andExpect(status().isOk());
	}

	@Test
	void aLinkFromBeforeTheUpgradeNoLongerLogsIn() throws Exception {
		String link = rowFromBeforeTheUpgrade("selin.acar@std.yildiz.edu.tr", "pre-upgrade-link", false);

		openLink(mockMvc, link)
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	private String requestLink(String schoolMail) throws Exception {
		return MailLogin.requestLink(mockMvc, smtp, schoolMail);
	}

	/**
	 * A user_tokens row as the version before this change wrote it: no kind. Its
	 * value was both the mailed link and, once opened, the session cookie.
	 */
	private String rowFromBeforeTheUpgrade(String schoolMail, String token, boolean opened) throws Exception {
		requestLink(schoolMail);
		database.update("""
				INSERT INTO user_tokens (user_id, token, created_at, user_ip, is_used, used_at)
				SELECT id, ?, now(), '127.0.0.1', ?, CASE WHEN ? THEN now() END FROM users WHERE school_mail = ?""",
				token, opened, opened, schoolMail);
		return token;
	}

	private ResultActions placeAPixelWith(Cookie userToken) throws Exception {
		return PlaceApi.placeAPixelWith(mockMvc, userToken);
	}

}
