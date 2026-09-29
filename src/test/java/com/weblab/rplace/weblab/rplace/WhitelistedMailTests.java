package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static com.weblab.rplace.weblab.rplace.MailLogin.requestLink;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whitelisted mail table stays, but nothing can add to it over HTTP any more.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class WhitelistedMailTests {

	@RegisterExtension
	static final GreenMailExtension smtp = MailLogin.smtpServer();

	@DynamicPropertySource
	static void mailServer(DynamicPropertyRegistry registry) {
		MailLogin.sendMailThrough(smtp, registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void thereIsNoEndpointForAddingAWhitelistedMail() throws Exception {
		mockMvc.perform(addWhitelistedMail()).andExpect(status().isForbidden());

		String link = requestLink(mockMvc, smtp, "deniz.arslan@std.yildiz.edu.tr");
		Cookie session = openLink(mockMvc, link).andReturn().getResponse().getCookie("user_token");
		mockMvc.perform(addWhitelistedMail().cookie(session)).andExpect(status().isNotFound());
	}

	private MockHttpServletRequestBuilder addWhitelistedMail() {
		return post("/api/whitelistedMails/add")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mail\":\"someone@example.com\",\"key\":\"any-key\"}");
	}

}
