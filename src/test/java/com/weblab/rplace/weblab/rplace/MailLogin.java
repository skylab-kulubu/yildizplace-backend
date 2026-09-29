package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The mail login flow over HTTP: ask for a link, read it from the mail GreenMail
 * received, open it. Each test class keeps its own GreenMail server.
 */
final class MailLogin {

	private static final String SENDER = "place@test.local";
	private static final String SMTP_PASSWORD = "smtp-test-password";
	private static final Pattern LINK_TOKEN = Pattern.compile("/play\\?token=([A-Za-z0-9_=-]+)");

	private MailLogin() {
	}

	static GreenMailExtension smtpServer() {
		return new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort())
				.withConfiguration(GreenMailConfiguration.aConfig().withUser(SENDER, SENDER, SMTP_PASSWORD))
				.withPerMethodLifecycle(false);
	}

	static void sendMailThrough(GreenMailExtension smtp, DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", () -> "127.0.0.1");
		registry.add("spring.mail.port", () -> smtp.getSmtp().getPort());
		registry.add("spring.mail.username", () -> SENDER);
		registry.add("spring.mail.password", () -> SMTP_PASSWORD);
	}

	/** Asks for a login link and returns the token in the link mailed to the address. */
	static String requestLink(MockMvc mockMvc, GreenMailExtension smtp, String schoolMail) throws Exception {
		mockMvc.perform(post("/api/users/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"schoolMail\":\"" + schoolMail + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		MimeMessage[] mails = smtp.getReceivedMessagesForDomain(schoolMail);
		assertThat(mails).isNotEmpty();
		Matcher link = LINK_TOKEN.matcher(textOf(mails[mails.length - 1]));
		assertThat(link.find()).isTrue();
		return link.group(1);
	}

	static ResultActions openLink(MockMvc mockMvc, String token) throws Exception {
		return mockMvc.perform(post("/api/users/login").param("token", token));
	}

	private static String textOf(Part part) throws Exception {
		Object content = part.getContent();
		if (content instanceof String text) {
			return text;
		}
		Multipart parts = (Multipart) content;
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < parts.getCount(); i++) {
			text.append(textOf(parts.getBodyPart(i)));
		}
		return text.toString();
	}

}
