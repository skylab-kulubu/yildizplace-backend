package com.weblab.rplace.weblab.rplace;

import com.weblab.rplace.weblab.rplace.business.abstracts.WhitelistedMailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whitelist entries are revoked and restored, never deleted (ADR 0042, data
 * lifecycle 06): a revoked entry keeps its row with when and by whom, stops
 * counting at once, and a moderator can bring it back. Only moderators and admins
 * see or change the list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
class WhitelistLifecycleTests {

	@DynamicPropertySource
	static void eskylab(DynamicPropertyRegistry registry) {
		FakeEskylab.configureBackend(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate database;

	@Autowired
	private WhitelistedMailService whitelist;

	@Test
	void aRevokedEntryKeepsItsRowWithWhenAndWhoAndStopsCountingAtOnce() throws Exception {
		Cookie moderator = moderator("liste.moderator1@std.yildiz.edu.tr");
		int id = entry("misafir1@example.com");
		assertThat(whitelist.existsByMail("misafir1@example.com").isSuccess()).isTrue();

		revoke(moderator, id).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		assertThat(whitelist.existsByMail("misafir1@example.com").isSuccess()).isFalse();
		var row = database.queryForMap("SELECT mail, revoked_at, revoked_by_id FROM whitelisted_mails WHERE id = ?", id);
		assertThat(row.get("mail")).isEqualTo("misafir1@example.com");
		assertThat(row.get("revoked_at")).isNotNull();
		assertThat(row.get("revoked_by_id")).isEqualTo(userId("liste.moderator1@std.yildiz.edu.tr"));
	}

	@Test
	void revokingTwiceAndRestoringTwiceChangeNothingTheSecondTime() throws Exception {
		Cookie moderator = moderator("liste.moderator2@std.yildiz.edu.tr");
		int id = entry("misafir2@example.com");

		revoke(moderator, id).andExpect(jsonPath("$.success").value(true));
		Object revokedAt = database.queryForObject("SELECT revoked_at FROM whitelisted_mails WHERE id = ?", Object.class, id);
		revoke(moderator, id).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
		assertThat(database.queryForObject("SELECT revoked_at FROM whitelisted_mails WHERE id = ?", Object.class, id)).isEqualTo(revokedAt);

		restore(moderator, id).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
		restore(moderator, id).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
		assertThat(whitelist.existsByMail("misafir2@example.com").isSuccess()).isTrue();
		assertThat(database.queryForObject("SELECT count(*) FROM whitelisted_mails WHERE id = ? AND revoked_at IS NULL AND revoked_by_id IS NULL",
				Integer.class, id)).isEqualTo(1);
	}

	@Test
	void theListShowsCurrentRevokedOrAllEntries() throws Exception {
		Cookie moderator = moderator("liste.moderator3@std.yildiz.edu.tr");
		int kept = entry("kalan@example.com");
		int revoked = entry("iptal@example.com");
		revoke(moderator, revoked);

		mockMvc.perform(get("/api/whitelistedMails").cookie(moderator))
				.andExpect(jsonPath("$.data[?(@.id == " + kept + ")]").exists())
				.andExpect(jsonPath("$.data[?(@.id == " + revoked + ")]").doesNotExist());
		mockMvc.perform(get("/api/whitelistedMails").param("lifecycle", "revoked").cookie(moderator))
				.andExpect(jsonPath("$.data[?(@.id == " + kept + ")]").doesNotExist())
				.andExpect(jsonPath("$.data[?(@.id == " + revoked + ")].revokedAt").exists());
		mockMvc.perform(get("/api/whitelistedMails").param("lifecycle", "all").cookie(moderator))
				.andExpect(jsonPath("$.data[?(@.id == " + kept + ")]").exists())
				.andExpect(jsonPath("$.data[?(@.id == " + revoked + ")]").exists());
	}

	@Test
	void restoringIsRefusedWhileAnotherEntryHoldsTheSameAddress() throws Exception {
		Cookie moderator = moderator("liste.moderator4@std.yildiz.edu.tr");
		int old = entry("cakisma@example.com");
		revoke(moderator, old);
		entry("Cakisma@Example.com");

		restore(moderator, old).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
		assertThat(database.queryForObject("SELECT revoked_at FROM whitelisted_mails WHERE id = ?", Object.class, old)).isNotNull();
	}

	@Test
	void anUnknownEntryIsNotFound() throws Exception {
		Cookie moderator = moderator("liste.moderator5@std.yildiz.edu.tr");

		revoke(moderator, 999999).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
		restore(moderator, 999999).andExpect(status().isNotFound());
	}

	@Test
	void revokingAndRestoringAreInTheAuditLog() throws Exception {
		Cookie moderator = moderator("liste.moderator6@std.yildiz.edu.tr");
		int id = entry("denetim@example.com");

		revoke(moderator, id);
		restore(moderator, id);

		assertThat(database.queryForList("SELECT action FROM moderation_audit_log WHERE target = 'denetim@example.com' ORDER BY id", String.class))
				.containsExactly("REVOKE_WHITELISTED_MAIL", "RESTORE_WHITELISTED_MAIL");
	}

	@Test
	void onlyModeratorsAndAdminsSeeOrChangeTheList() throws Exception {
		Cookie user = EskylabLogin.logIn(mockMvc, claims("school_email", "liste.kullanici@std.yildiz.edu.tr"))
				.andReturn().getResponse().getCookie("user_token");
		int id = entry("gizli@example.com");

		mockMvc.perform(get("/api/whitelistedMails").cookie(user)).andExpect(status().isForbidden());
		revoke(user, id).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/whitelistedMails")).andExpect(status().isForbidden());
		assertThat(database.queryForObject("SELECT revoked_at FROM whitelisted_mails WHERE id = ?", Object.class, id)).isNull();
	}

	@Test
	void thereIsStillNoWayToAddOrDeleteAnEntry() throws Exception {
		Cookie admin = EskylabLogin.logIn(mockMvc, claims("school_email", "liste.admin@std.yildiz.edu.tr",
				"resource_access", placeClientRoles("place:admin"))).andReturn().getResponse().getCookie("user_token");
		int id = entry("kalici@example.com");

		mockMvc.perform(post("/api/whitelistedMails/add").cookie(admin)
						.contentType(MediaType.APPLICATION_JSON).content("{\"mail\":\"yeni@example.com\"}"))
				.andExpect(status().isNotFound());
		int deleteAnswer = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/whitelistedMails/" + id).cookie(admin)).andReturn().getResponse().getStatus();
		assertThat(deleteAnswer).isIn(404, 405);
		assertThat(database.queryForObject("SELECT count(*) FROM whitelisted_mails WHERE id = ?", Integer.class, id)).isEqualTo(1);
	}

	private Cookie moderator(String schoolMail) throws Exception {
		return EskylabLogin.logIn(mockMvc, claims("school_email", schoolMail, "resource_access", placeClientRoles("place:moderator")))
				.andReturn().getResponse().getCookie("user_token");
	}

	private org.springframework.test.web.servlet.ResultActions revoke(Cookie session, int id) throws Exception {
		return mockMvc.perform(post("/api/whitelistedMails/" + id + "/revoke").cookie(session));
	}

	private org.springframework.test.web.servlet.ResultActions restore(Cookie session, int id) throws Exception {
		return mockMvc.perform(post("/api/whitelistedMails/" + id + "/restore").cookie(session));
	}

	/** An entry as the earlier versions added them: the address only. */
	private int entry(String mail) {
		return database.queryForObject("INSERT INTO whitelisted_mails (mail) VALUES (?) RETURNING id", Integer.class, mail);
	}

	private Integer userId(String schoolMail) {
		return database.queryForObject("SELECT id FROM users WHERE school_mail = ?", Integer.class, schoolMail);
	}

}
