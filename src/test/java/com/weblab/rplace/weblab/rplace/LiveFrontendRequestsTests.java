package com.weblab.rplace.weblab.rplace;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mail login as the frontend on place.yildizskylab.com sends it today: GET with
 * the address and the link token in the query string. Runs against a real server, so
 * the status codes are the ones a browser sees, error page included.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class LiveFrontendRequestsTests {

	@RegisterExtension
	static final GreenMailExtension smtp = MailLogin.smtpServer();

	@DynamicPropertySource
	static void mailServer(DynamicPropertyRegistry registry) {
		MailLogin.sendMailThrough(smtp, registry);
	}

	@Autowired
	private TestRestTemplate http;

	@Test
	void aLinkAskedForWithGetLogsInWithGet() throws Exception {
		String schoolMail = "deniz.koc@std.yildiz.edu.tr";

		ResponseEntity<Map> asked = http.getForEntity("/api/users/register?schoolMail={mail}", Map.class, schoolMail);
		assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(asked.getBody()).containsEntry("success", true);

		String token = MailLogin.tokenInLastMailTo(smtp, schoolMail);
		ResponseEntity<Map> opened = http.getForEntity("/api/users/login?token={token}", Map.class, token);
		assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(opened.getBody()).containsEntry("success", true);
		assertThat(opened.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(c -> c.startsWith("user_token="));
	}

	@Test
	void theQueryStringFormIsCountedInTheLogWithoutTheAddressOrTheLink(CapturedOutput output) throws Exception {
		String schoolMail = "sorgu.dizesi@std.yildiz.edu.tr";

		ResponseEntity<Map> asked = http.getForEntity("/api/users/register?schoolMail={mail}", Map.class, schoolMail);
		String token = MailLogin.tokenInLastMailTo(smtp, schoolMail);
		ResponseEntity<Map> opened = http.getForEntity("/api/users/login?token={token}", Map.class, token);

		assertThat(asked.getHeaders().getFirst("Deprecation")).isEqualTo("true");
		assertThat(opened.getHeaders().getFirst("Deprecation")).isEqualTo("true");
		// Place's own log; the test's SMTP server (GreenMail) logs the addresses it receives mail for.
		String placeLog = output.getAll().lines().filter(line -> !line.contains("c.icegreen.greenmail")).collect(Collectors.joining("\n"));
		assertThat(placeLog)
				.contains("/api/users/register: the school address came in the query string")
				.contains("/api/users/login: the login link came in the query string")
				.doesNotContain(schoolMail)
				.doesNotContain(token);
	}

	@Test
	void theBodyFormIsNotCounted(CapturedOutput output) {
		HttpHeaders json = new HttpHeaders();
		json.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<Map> asked = http.postForEntity("/api/users/register",
				new HttpEntity<>("{\"schoolMail\":\"govde.formu@std.yildiz.edu.tr\"}", json), Map.class);

		assertThat(asked.getHeaders().getFirst("Deprecation")).isNull();
		assertThat(output.getAll()).doesNotContain("/api/users/register: the school address came in the query string");
	}

	@Test
	void aLinkAskedForWithAJsonBodyStillWorks() {
		HttpHeaders json = new HttpHeaders();
		json.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<Map> asked = http.postForEntity("/api/users/register",
				new HttpEntity<>("{\"schoolMail\":\"cem.ozturk@std.yildiz.edu.tr\"}", json), Map.class);

		assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(asked.getBody()).containsEntry("success", true);
	}

	@Test
	void askingWithoutAnAddressIsRefusedWithAMessage() {
		ResponseEntity<Map> asked = http.getForEntity("/api/users/register", Map.class);

		assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(asked.getBody()).containsEntry("success", false);
	}

	@Test
	void aWrongMethodOnAPublicEndpointIsA405NotA403() {
		ResponseEntity<String> put = http.exchange("/api/users/register", HttpMethod.PUT, HttpEntity.EMPTY, String.class);

		assertThat(put.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
	}

	@Test
	void aProtectedEndpointStillRefusesAnonymousCallers() {
		ResponseEntity<String> board = http.getForEntity("/api/pixels/getBoard", String.class);

		assertThat(board.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

}
