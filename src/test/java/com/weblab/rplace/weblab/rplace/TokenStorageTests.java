package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.weblab.rplace.weblab.rplace.core.security.UserTokenHashing;
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

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static com.weblab.rplace.weblab.rplace.PlaceApi.placeAPixelWith;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * user_tokens keeps only the SHA-256 of login link and session values (ticket 08):
 * someone who reads the table cannot log in with what they read. Values written
 * in the clear by earlier versions keep working: the next start hashes them, and
 * one that arrives before that (a row an old instance wrote while the new one was
 * starting) is hashed the first time it is used.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
class TokenStorageTests {

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
	private UserTokenHashing userTokenHashing;

	@Test
	void aMailedLinkAndItsSessionAreStoredOnlyAsHashes() throws Exception {
		String link = MailLogin.requestLink(mockMvc, smtp, "ozet.mail@std.yildiz.edu.tr");
		Cookie session = openLink(mockMvc, link).andReturn().getResponse().getCookie("user_token");

		assertStoredOnlyAsHash(link);
		assertStoredOnlyAsHash(session.getValue());
		placeAPixelWith(mockMvc, session).andExpect(status().isOk());
	}

	@Test
	void anEskylabSessionIsStoredOnlyAsAHash() throws Exception {
		Cookie session = EskylabLogin.logIn(mockMvc, claims("school_email", "ozet.eskylab@std.yildiz.edu.tr"))
				.andReturn().getResponse().getCookie("user_token");

		assertStoredOnlyAsHash(session.getValue());
		placeAPixelWith(mockMvc, session).andExpect(status().isOk());
	}

	@Test
	void theStoredHashDoesNotLogIn() throws Exception {
		Cookie session = EskylabLogin.logIn(mockMvc, claims("school_email", "ozet.calan@std.yildiz.edu.tr"))
				.andReturn().getResponse().getCookie("user_token");

		placeAPixelWith(mockMvc, new Cookie("user_token", sha256(session.getValue()))).andExpect(status().isForbidden());
	}

	@Test
	void logoutRemovesTheHashedSession() throws Exception {
		Cookie session = EskylabLogin.logIn(mockMvc, claims("school_email", "ozet.cikis@std.yildiz.edu.tr"))
				.andReturn().getResponse().getCookie("user_token");

		mockMvc.perform(get("/api/users/logout").cookie(session)).andExpect(status().isOk());

		assertThat(rowsWithHash(session.getValue())).isZero();
		placeAPixelWith(mockMvc, session).andExpect(status().isForbidden());
	}

	@Test
	void theModeratorsTokenListShowsNoHashesNorTurnstileAnswers() throws Exception {
		Cookie moderator = EskylabLogin.logIn(mockMvc, claims("school_email", "ozet.moderator@std.yildiz.edu.tr",
				"resource_access", placeClientRoles("place:moderator"))).andReturn().getResponse().getCookie("user_token");

		String body = mockMvc.perform(get("/api/userTokens/getAll").cookie(moderator))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(sha256(moderator.getValue())).doesNotContain(moderator.getValue());
		assertThat(body).doesNotContain("tokenHash").doesNotContain("cloudflareToken").doesNotContain("legacyToken");
	}

	@Test
	void aSessionStoredInTheClearByAnEarlierVersionKeepsWorkingAndIsHashedOnUse() throws Exception {
		String session = rowInTheClear("acik.oturum@std.yildiz.edu.tr", "clear-session-before-hashing", null);

		placeAPixelWith(mockMvc, new Cookie("user_token", session)).andExpect(status().isOk());

		assertStoredOnlyAsHash(session);
		placeAPixelWith(mockMvc, new Cookie("user_token", session)).andExpect(status().isOk());
	}

	@Test
	void aLinkMailedInTheClearByAnEarlierVersionStillLogsIn() throws Exception {
		String link = rowInTheClear("acik.baglanti@std.yildiz.edu.tr", "clear-link-before-hashing", "LINK");

		Cookie session = openLink(mockMvc, link)
				.andExpect(jsonPath("$.success").value(true))
				.andReturn().getResponse().getCookie("user_token");

		assertStoredOnlyAsHash(link);
		placeAPixelWith(mockMvc, new Cookie("user_token", session.getValue())).andExpect(status().isOk());
	}

	@Test
	void theStartHashesEveryValueStoredInTheClear() throws Exception {
		String session = rowInTheClear("acik.baslangic@std.yildiz.edu.tr", "clear-session-at-start", "SESSION");
		String link = rowInTheClear("acik.baslangic2@std.yildiz.edu.tr", "clear-link-at-start", "LINK");

		// What every start does before Place answers requests.
		userTokenHashing.hashValuesStoredInTheClear();

		assertThat(database.queryForObject("SELECT count(*) FROM user_tokens WHERE token IS NOT NULL", Integer.class)).isZero();
		assertStoredOnlyAsHash(session);
		assertStoredOnlyAsHash(link);
		placeAPixelWith(mockMvc, new Cookie("user_token", session)).andExpect(status().isOk());
		openLink(mockMvc, link).andExpect(jsonPath("$.success").value(true));
	}

	private void assertStoredOnlyAsHash(String value) {
		List<String> columns = database.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'user_tokens' AND data_type IN ('character varying', 'text')",
				String.class);
		for (String column : columns) {
			Integer clear = database.queryForObject("SELECT count(*) FROM user_tokens WHERE " + column + " = ?", Integer.class, value);
			assertThat(clear).as("rows with the value in the clear in " + column).isZero();
		}
		assertThat(rowsWithHash(value)).isEqualTo(1);
	}

	private int rowsWithHash(String value) {
		return database.queryForObject("SELECT count(*) FROM user_tokens WHERE token_hash = ?", Integer.class, sha256(value));
	}

	/** A user_tokens row as the versions before hashing wrote it: the value itself in the token column. */
	private String rowInTheClear(String schoolMail, String token, String kind) throws Exception {
		MailLogin.requestLink(mockMvc, smtp, schoolMail);
		database.update("""
				INSERT INTO user_tokens (user_id, token, created_at, user_ip, is_used, kind)
				SELECT id, ?, now(), '127.0.0.1', false, CAST(? AS varchar) FROM users WHERE school_mail = ?""", token, kind, schoolMail);
		return token;
	}

	static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
