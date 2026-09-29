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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static com.weblab.rplace.weblab.rplace.PlaceApi.placeAPixelWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLACE_LOGIN_LINK_SINGLE_USE is false by default: a mailed link logs in again
 * until PLACE_LOGIN_LINK_TTL has passed, since a mail scanner may open the link
 * before the student does. Every use opens a new session; the link value itself
 * never is one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
class ReusableLoginLinkTests {

	@RegisterExtension
	static final GreenMailExtension smtp = MailLogin.smtpServer();

	@DynamicPropertySource
	static void mailServer(DynamicPropertyRegistry registry) {
		MailLogin.sendMailThrough(smtp, registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestClock clock;

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aLinkLogsInAgainWithinItsLifetimeUnderANewSessionEachTime() throws Exception {
		String link = MailLogin.requestLink(mockMvc, smtp, "gizem.tas@std.yildiz.edu.tr");

		Cookie first = openLink(mockMvc, link)
				.andExpect(jsonPath("$.success").value(true))
				.andReturn().getResponse().getCookie("user_token");
		Cookie second = openLink(mockMvc, link)
				.andExpect(jsonPath("$.success").value(true))
				.andReturn().getResponse().getCookie("user_token");

		assertThat(second.getValue()).isNotEqualTo(first.getValue()).isNotEqualTo(link);
		placeAPixelWith(mockMvc, first).andExpect(status().isOk());
		placeAPixelWith(mockMvc, second).andExpect(status().isOk());
	}

	@Test
	void theLinkValueIsNotASession() throws Exception {
		String link = MailLogin.requestLink(mockMvc, smtp, "emre.koc@std.yildiz.edu.tr");
		openLink(mockMvc, link).andExpect(jsonPath("$.success").value(true));

		placeAPixelWith(mockMvc, new Cookie("user_token", link)).andExpect(status().isForbidden());
	}

	@Test
	void anOpenedLinkStopsWorkingAfterItsLifetime() throws Exception {
		String link = MailLogin.requestLink(mockMvc, smtp, "sena.yurt@std.yildiz.edu.tr");
		openLink(mockMvc, link).andExpect(jsonPath("$.success").value(true));
		clock.advance(Duration.ofMinutes(61));

		openLink(mockMvc, link)
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(cookie().doesNotExist("user_token"));
	}

}
