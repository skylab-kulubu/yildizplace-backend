package com.weblab.rplace.weblab.rplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas socket (/rplace, STOMP over SockJS) opens only for the origins that may call
 * /api/** (place.cors.allowed-origins, CorsConfig): in production the https frontend alone.
 * A page on any other origin, a sibling subdomain or the frontend's dev server included,
 * gets 403 at the handshake, so it cannot ride a visitor's browser into the socket
 * (cross-site WebSocket hijacking). The dev profile adds the dev server (DevProfileCorsTests).
 *
 * <p>Real handshakes against a running server: MockMvc cannot upgrade a connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class WebSocketOriginTests {

	private static final String FRONTEND = "https://place.yildizskylab.com";

	private static final String[] OTHER_ORIGINS = {
			"https://evil.example",
			"https://evil.yildizskylab.com",
			"http://place.yildizskylab.com",
			"http://localhost:3000",
	};

	private static final AtomicInteger sessions = new AtomicInteger();

	private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@LocalServerPort
	private int port;

	@Test
	void placesFrontendOpensTheCanvasSocket() throws Exception {
		// What the live frontend does: SockJS asks /info, then opens its websocket transport.
		HttpResponse<String> info = info(FRONTEND);
		assertThat(info.statusCode()).isEqualTo(200);
		assertThat(info.headers().firstValue("Access-Control-Allow-Origin")).hasValue(FRONTEND);

		FirstMessage sockJs = new FirstMessage();
		WebSocket socket = open(sockJsTransport(), FRONTEND, sockJs).get(10, SECONDS);
		assertThat(sockJs.text.get(10, SECONDS)).isEqualTo("o"); // SockJS "open" frame
		socket.abort();

		open("/rplace/websocket", FRONTEND, new FirstMessage()).get(10, SECONDS).abort();
	}

	@Test
	void aPageOnAnotherOriginCannotOpenTheCanvasSocket() throws Exception {
		for (String origin : OTHER_ORIGINS) {
			assertThat(handshakeStatus(sockJsTransport(), origin)).as("SockJS websocket from " + origin).isEqualTo(403);
			assertThat(handshakeStatus("/rplace/websocket", origin)).as("raw websocket from " + origin).isEqualTo(403);

			HttpResponse<String> info = info(origin);
			assertThat(info.statusCode()).as("SockJS info from " + origin).isEqualTo(403);
			assertThat(info.headers().firstValue("Access-Control-Allow-Origin")).as(origin).isEmpty();
		}
	}

	@Test
	void aClientThatIsNotABrowserStillConnects() throws Exception {
		// Browsers always send Origin; the check protects visitors' browsers, not the socket itself.
		open("/rplace/websocket", null, new FirstMessage()).get(10, SECONDS).abort();
	}

	private int handshakeStatus(String path, String origin) throws Exception {
		try {
			open(path, origin, new FirstMessage()).get(10, SECONDS).abort();
			return 101;
		} catch (ExecutionException e) {
			if (e.getCause() instanceof WebSocketHandshakeException refused) {
				return refused.getResponse().statusCode();
			}
			throw e;
		}
	}

	private CompletableFuture<WebSocket> open(String path, String origin, WebSocket.Listener listener) {
		WebSocket.Builder builder = http.newWebSocketBuilder();
		if (origin != null) {
			builder.header("Origin", origin);
		}
		return builder.buildAsync(URI.create("ws://localhost:" + port + path), listener);
	}

	private HttpResponse<String> info(String origin) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/rplace/info"))
				.header("Origin", origin).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	/** A fresh SockJS session: /rplace/{server}/{session}/websocket. */
	private static String sockJsTransport() {
		return "/rplace/000/origin" + sessions.incrementAndGet() + "/websocket";
	}

	private static final class FirstMessage implements WebSocket.Listener {

		final CompletableFuture<String> text = new CompletableFuture<>();

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			text.complete(data.toString());
			return WebSocket.Listener.super.onText(webSocket, data, last);
		}
	}

}
