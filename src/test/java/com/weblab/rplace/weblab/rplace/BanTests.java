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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.FRONTEND;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static com.weblab.rplace.weblab.rplace.MailLogin.openLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bans hold at every door (ticket 08): a banned user or a request from a banned
 * address cannot ask for a link, open one, log in with e-skylab or use a session
 * it already has. Moderators and admins lift bans; every ban and unban is written
 * to the moderation audit log with who did it.
 *
 * <p>An address ban does not stop an elevated session or login: on a shared
 * network (the campus) a moderator could otherwise lock themselves out of the
 * very endpoint that lifts the ban.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
class BanTests {

	private static final String USER_BANNED = "Bu kullanıcı yasaklı!";
	private static final String IP_BANNED = "Bu IP adresi yasaklı!";

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
	void aBannedUsersSessionEndsOnItsNextRequest() throws Exception {
		Cookie moderator = moderator("yasak.moderator1@std.yildiz.edu.tr");
		Cookie user = eskylabSession("yasak.oturum@std.yildiz.edu.tr");

		banUser(moderator, "yasak.oturum@std.yildiz.edu.tr").andExpect(jsonPath("$.success").value(true));

		PlaceApi.placeAPixelWith(mockMvc, user)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value(USER_BANNED))
				.andExpect(cookie().maxAge("user_token", 0));
		assertThat(sessionsOf("yasak.oturum@std.yildiz.edu.tr")).isZero();
		// The board stays public.
		mockMvc.perform(get("/api/pixels/getColors").cookie(user)).andExpect(status().isOk());
	}

	@Test
	void aBannedUserCannotOpenALinkMailedBeforeTheBan() throws Exception {
		Cookie moderator = moderator("yasak.moderator2@std.yildiz.edu.tr");
		String link = MailLogin.requestLink(mockMvc, smtp, "yasak.baglanti@std.yildiz.edu.tr");

		banUser(moderator, "yasak.baglanti@std.yildiz.edu.tr");

		openLink(mockMvc, link)
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value(USER_BANNED))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	@Test
	void aBannedUserCannotLogInWithEskylab() throws Exception {
		Cookie moderator = moderator("yasak.moderator3@std.yildiz.edu.tr");
		eskylabSession("yasak.eskylab@std.yildiz.edu.tr");
		banUser(moderator, "yasak.eskylab@std.yildiz.edu.tr");

		EskylabLogin.logIn(mockMvc, claims("school_email", "yasak.eskylab@std.yildiz.edu.tr"))
				.andExpect(redirectedUrl(FRONTEND + "/?sso=banned"))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	@Test
	void aBannedAddressCannotAskForALink() throws Exception {
		banIp(moderator("yasak.moderator4@std.yildiz.edu.tr"), "203.0.113.10");

		mockMvc.perform(post("/api/users/register").with(TraefikProxy.forwarding("203.0.113.10"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"schoolMail\":\"yasak.ipkayit@std.yildiz.edu.tr\"}"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value(IP_BANNED));
	}

	@Test
	void aBannedAddressCannotOpenALink() throws Exception {
		String link = MailLogin.requestLink(mockMvc, smtp, "yasak.ipbaglanti@std.yildiz.edu.tr");
		banIp(moderator("yasak.moderator5@std.yildiz.edu.tr"), "203.0.113.11");

		mockMvc.perform(post("/api/users/login").param("token", link).with(TraefikProxy.forwarding("203.0.113.11")))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value(IP_BANNED))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	@Test
	void aBannedAddressCannotLogInWithEskylab() throws Exception {
		banIp(moderator("yasak.moderator6@std.yildiz.edu.tr"), "203.0.113.12");

		EskylabLogin.logInFrom(mockMvc, "203.0.113.12", claims("school_email", "yasak.ipeskylab@std.yildiz.edu.tr"))
				.andExpect(redirectedUrl(FRONTEND + "/?sso=banned"))
				.andExpect(cookie().doesNotExist("user_token"));
	}

	@Test
	void aSessionUsedFromABannedAddressIsRefusedButNotEnded() throws Exception {
		Cookie user = eskylabSession("yasak.ipoturum@std.yildiz.edu.tr");
		banIp(moderator("yasak.moderator7@std.yildiz.edu.tr"), "203.0.113.13");

		PlaceApi.placeAPixelWith(mockMvc, user, "203.0.113.13")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(IP_BANNED));
		PlaceApi.placeAPixelWith(mockMvc, user, "198.51.100.1").andExpect(status().isOk());
	}

	@Test
	void aModeratorOnABannedNetworkCanStillLogInAndLiftTheBan() throws Exception {
		banIp(moderator("yasak.moderator8@std.yildiz.edu.tr"), "203.0.113.14");

		Cookie moderator = EskylabLogin.logInFrom(mockMvc, "203.0.113.14", claims("school_email", "kampus.moderator@std.yildiz.edu.tr",
						"resource_access", placeClientRoles("place:moderator")))
				.andExpect(cookie().exists("user_token"))
				.andReturn().getResponse().getCookie("user_token");

		mockMvc.perform(post("/api/bans/unbanIp").cookie(moderator).with(TraefikProxy.forwarding("203.0.113.14"))
						.param("ip", "203.0.113.14").param("reason", "kampüs ağı"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		assertThat(database.queryForObject("SELECT count(*) FROM banned_ips WHERE ip = '203.0.113.14'", Integer.class)).isZero();
	}

	@Test
	void aBannedAddressCannotHideBehindAForgedForwardedFor() throws Exception {
		banIp(moderator("yasak.moderator11@std.yildiz.edu.tr"), "203.0.113.15");

		// The client wrote the left entry itself; Traefik appended the address it saw.
		mockMvc.perform(post("/api/users/register").with(TraefikProxy.forwarding("192.0.2.15, 203.0.113.15"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"schoolMail\":\"yasak.sahte@std.yildiz.edu.tr\"}"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value(IP_BANNED));
	}

	@Test
	void aForwardedForSentPastTheProxyIsIgnored() throws Exception {
		banIp(moderator("yasak.moderator12@std.yildiz.edu.tr"), "198.51.100.40");

		mockMvc.perform(post("/api/users/register").with(TraefikProxy.directFrom("198.51.100.40", "192.0.2.40"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"schoolMail\":\"yasak.dogrudan@std.yildiz.edu.tr\"}"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value(IP_BANNED));
	}

	@Test
	void aLinkRequestRecordsTheAddressTraefikSawNotOneTheClientWrote() throws Exception {
		mockMvc.perform(post("/api/users/register").with(TraefikProxy.forwarding("192.0.2.41, 203.0.113.41"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"schoolMail\":\"adres.traefik@std.yildiz.edu.tr\"}"))
				.andExpect(jsonPath("$.success").value(true));
		mockMvc.perform(post("/api/users/register").with(TraefikProxy.directFrom("198.51.100.42", "192.0.2.42"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"schoolMail\":\"adres.dogrudan@std.yildiz.edu.tr\"}"))
				.andExpect(jsonPath("$.success").value(true));

		assertThat(linkAddressesOf("adres.traefik@std.yildiz.edu.tr")).containsExactly("203.0.113.41");
		assertThat(linkAddressesOf("adres.dogrudan@std.yildiz.edu.tr")).containsExactly("198.51.100.42");
	}

	@Test
	void anUnbannedUserCanLogInAgain() throws Exception {
		Cookie moderator = moderator("yasak.moderator9@std.yildiz.edu.tr");
		eskylabSession("af.kisi@std.yildiz.edu.tr");
		banUser(moderator, "af.kisi@std.yildiz.edu.tr");

		mockMvc.perform(post("/api/bans/unbanUser").cookie(moderator).param("schoolMail", "af.kisi@std.yildiz.edu.tr").param("reason", "itiraz kabul"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		EskylabLogin.logIn(mockMvc, claims("school_email", "af.kisi@std.yildiz.edu.tr")).andExpect(cookie().exists("user_token"));
		assertThat(bansOf("af.kisi@std.yildiz.edu.tr")).isZero();
	}

	@Test
	void liftingABanThatDoesNotExistSaysSo() throws Exception {
		Cookie moderator = moderator("yasak.moderator10@std.yildiz.edu.tr");
		eskylabSession("yasaksiz.kisi@std.yildiz.edu.tr");

		mockMvc.perform(post("/api/bans/unbanUser").cookie(moderator).param("schoolMail", "yasaksiz.kisi@std.yildiz.edu.tr"))
				.andExpect(jsonPath("$.success").value(false));
		mockMvc.perform(post("/api/bans/unbanIp").cookie(moderator).param("ip", "192.0.2.99"))
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void onlyModeratorsAndAdminsLiftBansOrReadTheAuditLog() throws Exception {
		Cookie user = eskylabSession("sade.kullanici@std.yildiz.edu.tr");

		mockMvc.perform(post("/api/bans/unbanUser").cookie(user).param("schoolMail", "x@std.yildiz.edu.tr")).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/bans/unbanIp").cookie(user).param("ip", "192.0.2.1")).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/bans/getAuditLog").cookie(user)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/bans/getAuditLog")).andExpect(status().isForbidden());
	}

	@Test
	void everyBanAndUnbanIsInTheAuditLogWithWhoDidIt() throws Exception {
		Cookie admin = EskylabLogin.logIn(mockMvc, claims("school_email", "denetim.admin@std.yildiz.edu.tr",
				"resource_access", placeClientRoles("place:admin"))).andReturn().getResponse().getCookie("user_token");
		eskylabSession("denetim.hedef@std.yildiz.edu.tr");

		banUser(admin, "denetim.hedef@std.yildiz.edu.tr");
		mockMvc.perform(post("/api/bans/unbanUser").cookie(admin).param("schoolMail", "denetim.hedef@std.yildiz.edu.tr").param("reason", "hata"));
		banIp(admin, "192.0.2.50");
		mockMvc.perform(post("/api/bans/unbanIp").cookie(admin).param("ip", "192.0.2.50"));

		Integer adminId = database.queryForObject("SELECT id FROM users WHERE school_mail = 'denetim.admin@std.yildiz.edu.tr'", Integer.class);
		var entries = database.queryForList(
				"SELECT action, target, reason, actor_user_id, actor_role FROM moderation_audit_log WHERE actor_user_id = ? ORDER BY id", adminId);
		assertThat(entries).extracting(row -> row.get("action"))
				.containsExactly("BAN_USER", "UNBAN_USER", "BAN_IP", "UNBAN_IP");
		assertThat(entries).extracting(row -> row.get("target"))
				.containsExactly("denetim.hedef@std.yildiz.edu.tr", "denetim.hedef@std.yildiz.edu.tr", "192.0.2.50", "192.0.2.50");
		assertThat(entries.get(1).get("reason")).isEqualTo("hata");
		assertThat(entries).allSatisfy(row -> assertThat(row.get("actor_role")).isEqualTo("ROLE_ADMIN"));

		mockMvc.perform(get("/api/bans/getAuditLog").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[?(@.action == 'UNBAN_IP' && @.target == '192.0.2.50')]").exists());
	}

	@Test
	void aModeratorCannotBanThemselves() throws Exception {
		Cookie moderator = moderator("kendini.yasaklayan@std.yildiz.edu.tr");

		banUser(moderator, "kendini.yasaklayan@std.yildiz.edu.tr").andExpect(jsonPath("$.success").value(false));
		assertThat(bansOf("kendini.yasaklayan@std.yildiz.edu.tr")).isZero();
	}

	private Cookie moderator(String schoolMail) throws Exception {
		return EskylabLogin.logIn(mockMvc, claims("school_email", schoolMail, "resource_access", placeClientRoles("place:moderator")))
				.andReturn().getResponse().getCookie("user_token");
	}

	private Cookie eskylabSession(String schoolMail) throws Exception {
		return EskylabLogin.logIn(mockMvc, claims("school_email", schoolMail)).andReturn().getResponse().getCookie("user_token");
	}

	private ResultActions banUser(Cookie moderator, String schoolMail) throws Exception {
		return mockMvc.perform(post("/api/bans/banUser").cookie(moderator).param("schoolMail", schoolMail).param("reason", "test"));
	}

	private void banIp(Cookie moderator, String ip) throws Exception {
		mockMvc.perform(post("/api/bans/banIp").cookie(moderator).param("ip", ip).param("reason", "test"))
				.andExpect(jsonPath("$.success").value(true));
	}

	private int bansOf(String schoolMail) {
		return database.queryForObject(
				"SELECT count(*) FROM banned_users b JOIN users u ON u.id = b.banned_user_id WHERE u.school_mail = ?", Integer.class, schoolMail);
	}

	private java.util.List<String> linkAddressesOf(String schoolMail) {
		return database.queryForList(
				"SELECT t.user_ip FROM user_tokens t JOIN users u ON u.id = t.user_id WHERE u.school_mail = ? AND t.kind = 'LINK'", String.class, schoolMail);
	}

	private int sessionsOf(String schoolMail) {
		return database.queryForObject(
				"SELECT count(*) FROM user_tokens t JOIN users u ON u.id = t.user_id WHERE u.school_mail = ? AND t.kind = 'SESSION'", Integer.class, schoolMail);
	}

}
