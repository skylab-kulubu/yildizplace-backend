package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requesting a login link sends it over SMTP to the school address that asked for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class LoginCodeMailTests {

	private static final String SENDER = "place@test.local";
	private static final String SMTP_PASSWORD = "smtp-test-password";

	@RegisterExtension
	static final GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort())
			.withConfiguration(GreenMailConfiguration.aConfig().withUser(SENDER, SENDER, SMTP_PASSWORD))
			.withPerMethodLifecycle(false);

	@DynamicPropertySource
	static void mailServer(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", () -> "127.0.0.1");
		registry.add("spring.mail.port", () -> smtp.getSmtp().getPort());
		registry.add("spring.mail.username", () -> SENDER);
		registry.add("spring.mail.password", () -> SMTP_PASSWORD);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void requestingALoginLinkMailsItToTheSchoolAddress() throws Exception {
		mockMvc.perform(post("/api/users/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"schoolMail\":\"ayse.yilmaz@std.yildiz.edu.tr\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue();
		MimeMessage[] received = smtp.getReceivedMessages();
		assertThat(received).hasSize(1);
		assertThat(received[0].getRecipients(Message.RecipientType.TO))
				.extracting(Object::toString)
				.containsExactly("ayse.yilmaz@std.yildiz.edu.tr");
		assertThat(received[0].getSubject()).isEqualTo("YıldızPlace Giriş Bağlantısı");
	}

}
