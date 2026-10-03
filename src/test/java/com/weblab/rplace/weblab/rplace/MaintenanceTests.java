package com.weblab.rplace.weblab.rplace;

import com.weblab.rplace.weblab.rplace.core.security.PlaceMaintenance;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static com.weblab.rplace.weblab.rplace.FakeEskylab.claims;
import static com.weblab.rplace.weblab.rplace.FakeEskylab.placeClientRoles;
import static com.weblab.rplace.weblab.rplace.PlaceApi.placeAPixelWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A scheduled job removes what has ended but would otherwise stay in the database
 * (ticket 08): elevated sessions past their end that nobody used again, and
 * e-skylab login attempts nobody came back from. It also hashes any login value
 * still stored in the clear.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, TestClock.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "place.maintenance.enabled=true")
class MaintenanceTests {

	@DynamicPropertySource
	static void eskylab(DynamicPropertyRegistry registry) {
		FakeEskylab.configureBackend(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate database;

	@Autowired
	private TestClock clock;

	@Autowired
	private PlaceMaintenance maintenance;

	@Autowired
	private ScheduledTaskHolder scheduledTasks;

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void theCleanupRunsOnASchedule() {
		assertThat(scheduledTasks.getScheduledTasks())
				.anySatisfy(task -> assertThat(task.getTask().getRunnable().toString()).contains("PlaceMaintenance"));
	}

	@Test
	void endedElevatedSessionsAndAbandonedLoginsAreRemovedOthersStay() throws Exception {
		Cookie ended = session("bitmis.yetkili@std.yildiz.edu.tr", "place:admin");
		Cookie user = session("surekli.kullanici@std.yildiz.edu.tr");
		clock.advance(Duration.ofHours(7));
		Cookie fresh = session("taze.yetkili@std.yildiz.edu.tr", "place:moderator");
		// Sent to Keycloak, never came back.
		EskylabLogin.start(mockMvc);
		clock.advance(Duration.ofHours(1));
		assertThat(database.queryForObject("SELECT count(*) FROM eskylab_login_attempts", Integer.class)).isEqualTo(1);

		maintenance.run();

		assertThat(sessionsOf("bitmis.yetkili@std.yildiz.edu.tr")).isZero();
		assertThat(sessionsOf("surekli.kullanici@std.yildiz.edu.tr")).isEqualTo(1);
		assertThat(sessionsOf("taze.yetkili@std.yildiz.edu.tr")).isEqualTo(1);
		assertThat(database.queryForObject("SELECT count(*) FROM eskylab_login_attempts", Integer.class)).isZero();
		placeAPixelWith(mockMvc, user).andExpect(status().isOk());
		placeAPixelWith(mockMvc, fresh).andExpect(status().isOk());
		placeAPixelWith(mockMvc, ended).andExpect(status().isForbidden());
	}

	@Test
	void valuesStoredInTheClearAreHashed() throws Exception {
		session("acik.bakim@std.yildiz.edu.tr");
		database.update("""
				INSERT INTO user_tokens (user_id, token, created_at, is_used)
				SELECT id, 'clear-before-maintenance', now(), false FROM users WHERE school_mail = 'acik.bakim@std.yildiz.edu.tr'""");

		maintenance.run();

		assertThat(database.queryForObject("SELECT count(*) FROM user_tokens WHERE token IS NOT NULL", Integer.class)).isZero();
		placeAPixelWith(mockMvc, new Cookie("user_token", "clear-before-maintenance")).andExpect(status().isOk());
	}

	private Cookie session(String schoolMail, String... placeRoles) throws Exception {
		return EskylabLogin.logIn(mockMvc, claims("school_email", schoolMail, "resource_access", placeClientRoles(placeRoles)))
				.andReturn().getResponse().getCookie("user_token");
	}

	private int sessionsOf(String schoolMail) {
		return database.queryForObject(
				"SELECT count(*) FROM user_tokens t JOIN users u ON u.id = t.user_id WHERE u.school_mail = ? AND t.kind = 'SESSION'", Integer.class, schoolMail);
	}

}
