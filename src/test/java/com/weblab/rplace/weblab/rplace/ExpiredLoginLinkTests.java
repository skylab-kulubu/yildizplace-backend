package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static com.weblab.rplace.weblab.rplace.MailLogin.requestLink;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A login link stops working once PLACE_LOGIN_LINK_TTL has passed. With a TTL of
 * zero every link is already expired when it arrives.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "place.login.link-ttl=0s")
class ExpiredLoginLinkTests {

	@RegisterExtension
	static final GreenMailExtension smtp = MailLogin.smtpServer();

	@DynamicPropertySource
	static void mailServer(DynamicPropertyRegistry registry) {
		MailLogin.sendMailThrough(smtp, registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void anExpiredLinkDoesNotLogIn() throws Exception {
		String link = requestLink(mockMvc, smtp, "can.ozturk@std.yildiz.edu.tr");

		openLink(mockMvc, link)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(cookie().doesNotExist("user_token"));
	}

}
