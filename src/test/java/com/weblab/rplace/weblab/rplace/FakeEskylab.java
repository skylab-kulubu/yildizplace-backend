package com.weblab.rplace.weblab.rplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stand-in for e-skylab's Keycloak: mock-oauth2-server (real discovery, JWKS,
 * signed ID tokens with the nonce of the request, PKCE checked at the token
 * endpoint) in a container. Everything reaches it through a small proxy that
 * records each request, so a test can see what the backend sent to Keycloak and
 * that it sent nothing. One instance serves the whole test run.
 *
 * <p>A person logs in on its login form with the claims their ID token should
 * carry (the form's claims override the defaults, including aud, exp and nonce).
 * It has no single sign-on session and ignores prompt=none, so a test plays
 * Keycloak's login_required answer itself. The mock also derives the token
 * response's expires_in from the ID token; Keycloak derives it from the access
 * token, so an ID token that expired in the past still comes with a positive
 * expires_in, and the proxy makes the mock's answer look like that.
 */
final class FakeEskylab {

	static final String CLIENT_ID = "place";
	static final String CLIENT_SECRET = "test-client-secret";
	static final String REDIRECT_URI = "http://localhost/api/auth/eskylab/callback";
	static final String FRONTEND = "https://place.test";

	private static final String REALM = "e-skylab";
	private static final Set<String> HOP_BY_HOP = Set.of("connection", "content-length", "transfer-encoding", "keep-alive");

	private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("ghcr.io/navikt/mock-oauth2-server:6.0.4")
			.withExposedPorts(8080)
			.waitingFor(Wait.forHttp("/" + REALM + "/.well-known/openid-configuration").forStatusCode(200));

	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
	private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();
	private static final ObjectMapper JSON = new ObjectMapper();

	private static HttpServer proxy;

	/** A request that reached Keycloak. */
	record Request(String method, String path, String authorization, String body) {
	}

	private FakeEskylab() {
	}

	/** Points the backend's e-skylab login at this Keycloak. */
	static void configureBackend(DynamicPropertyRegistry registry) {
		registry.add("keycloak.issuer", FakeEskylab::issuer);
		registry.add("keycloak.client-id", () -> CLIENT_ID);
		registry.add("keycloak.client-secret", () -> CLIENT_SECRET);
		registry.add("keycloak.redirect-uri", () -> REDIRECT_URI);
		registry.add("place.frontend-url", () -> FRONTEND);
	}

	static String issuer() {
		return "http://localhost:" + start().getAddress().getPort() + "/" + REALM;
	}

	/** Every request Keycloak has received so far, in order. */
	static List<Request> requests() {
		return List.copyOf(REQUESTS);
	}

	/**
	 * The person logs in on Keycloak's page that the authorization request opened,
	 * and Keycloak sends the browser back: returns that redirect (the callback with
	 * code and state).
	 */
	static URI logIn(URI authorizationRequest, Map<String, Object> idTokenClaims) throws Exception {
		String form = "username=" + URLEncoder.encode("e-skylab-user", UTF_8)
				+ "&claims=" + URLEncoder.encode(JSON.writeValueAsString(idTokenClaims), UTF_8);
		HttpResponse<String> page = HTTP.send(HttpRequest.newBuilder(authorizationRequest)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(page.statusCode()).as("Keycloak's answer to the login").isEqualTo(302);
		return URI.create(page.headers().firstValue("Location").orElseThrow());
	}

	/** Claims for an ID token, as key-value pairs. */
	static Map<String, Object> claims(Object... keysAndValues) {
		Map<String, Object> claims = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			claims.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return claims;
	}

	private static synchronized HttpServer start() {
		if (proxy != null) {
			return proxy;
		}
		KEYCLOAK.start();
		String target = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
		try {
			proxy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		proxy.createContext("/", exchange -> forward(exchange, target));
		proxy.start();
		return proxy;
	}

	// expires_in is the access token's lifetime at Keycloak, never negative.
	private static byte[] withKeycloaksExpiresIn(byte[] tokenResponse) throws IOException {
		JsonNode json = JSON.readTree(tokenResponse);
		if (json instanceof ObjectNode answer && answer.path("expires_in").asLong() < 0) {
			answer.put("expires_in", 300);
			return JSON.writeValueAsBytes(answer);
		}
		return tokenResponse;
	}

	private static void forward(HttpExchange exchange, String target) throws IOException {
		try (exchange) {
			byte[] body = exchange.getRequestBody().readAllBytes();
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			REQUESTS.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), authorization, new String(body, UTF_8)));

			// Keycloak builds its URLs from the Host header's host and this port: the proxy's.
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target + exchange.getRequestURI()))
					.method(exchange.getRequestMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
					.header("X-Forwarded-Port", String.valueOf(proxy.getAddress().getPort()));
			for (String header : List.of("Content-Type", "Authorization", "Accept")) {
				String value = exchange.getRequestHeaders().getFirst(header);
				if (value != null) {
					request.header(header, value);
				}
			}

			HttpResponse<byte[]> answer;
			try {
				answer = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				exchange.sendResponseHeaders(502, -1);
				return;
			}
			answer.headers().map().forEach((name, values) -> {
				if (!HOP_BY_HOP.contains(name.toLowerCase())) {
					exchange.getResponseHeaders().put(name, values);
				}
			});
			byte[] answerBody = exchange.getRequestURI().getPath().endsWith("/token")
					? withKeycloaksExpiresIn(answer.body())
					: answer.body();
			exchange.sendResponseHeaders(answer.statusCode(), answerBody.length == 0 ? -1 : answerBody.length);
			if (answerBody.length > 0) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(answerBody);
				}
			}
		}
	}

}
