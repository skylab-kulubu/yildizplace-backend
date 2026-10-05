package com.weblab.rplace.weblab.rplace;

import com.weblab.rplace.weblab.rplace.entities.dtos.FillDto;
import com.weblab.rplace.weblab.rplace.entities.dtos.PixelDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Only the server speaks on the canvas socket: PixelController broadcasts every pixel and fill
 * it accepted to /topic/pixels and /topic/fill. A client connects, subscribes and keeps the
 * connection alive; the live frontend never sends a message of its own (pixels go over HTTP,
 * where login, cooldown and bans apply). The simple broker would relay a client's SEND to every
 * subscriber, so anyone who can open the socket could paint on every live canvas.
 *
 * <p>Real STOMP frames against a running server, from the frontend's origin: the origin check
 * (WebSocketOriginTests) does not stop a page on that origin or a client that is not a browser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class WebSocketClientSendTests {

	private static final String FRONTEND = "https://place.yildizskylab.com";

	private static final String FAKE = "{\"x\":7,\"y\":7,\"color\":\"fake00\"}";

	private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@LocalServerPort
	private int port;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@ParameterizedTest
	@CsvSource({
			"SEND, /topic/pixels",
			"SEND, /topic/fill",
			// A server frame sent by a client: the same message type, the same relay.
			"MESSAGE, /topic/pixels",
	})
	void aClientCannotBroadcastToOtherViewers(String command, String destination) throws Exception {
		try (Stomp viewer = connect(); Stomp attacker = connect()) {
			viewer.subscribe("sub-0", destination);
			awaitSubscription(viewer, destination);

			attacker.send(command, headers("destination", destination, "content-type", "application/json"), FAKE);
			// Refused: an ERROR frame, then the connection closes. Relayed: nothing comes back,
			// and the wait gives the relayed frame time to reach the viewer.
			Frame reply = attacker.poll(3, SECONDS);

			messagingTemplate.convertAndSend(destination, new PixelDto(1, 2, "123456"));

			assertThat(viewer.bodiesUntil("123456")).as("what the viewer received on " + destination)
					.containsExactly("{\"x\":1,\"y\":2,\"color\":\"123456\"}");
			assertThat(reply).as("the server's answer to the client's " + command).isNotNull();
			assertThat(reply.command()).isEqualTo("ERROR");
			assertThat(attacker.closed).succeedsWithin(10, SECONDS);
		}
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void aBatchOfFramesLeavesOneLogLine(CapturedOutput output) throws Exception {
		// A hand-written client can pack many frames into one WebSocket message; the server reads
		// them all even after it has refused the first one and closed the connection.
		try (Stomp viewer = connect(); Stomp attacker = connect()) {
			viewer.subscribe("sub-0", "/topic/pixels");
			awaitSubscription(viewer, "/topic/pixels");

			StringBuilder batch = new StringBuilder();
			for (int i = 0; i < 100; i++) {
				batch.append("SEND\ndestination:/topic/pixels\n\n").append(FAKE).append('\0');
			}
			attacker.socket.sendText(batch, true).join();
			Frame reply = attacker.poll(3, SECONDS);
			assertThat(attacker.closed).succeedsWithin(10, SECONDS);

			messagingTemplate.convertAndSend("/topic/pixels", new PixelDto(5, 6, "654321"));
			assertThat(viewer.bodiesUntil("654321")).containsExactly("{\"x\":5,\"y\":6,\"color\":\"654321\"}");
			assertThat(reply).isNotNull();
			assertThat(reply.command()).isEqualTo("ERROR");
			assertThat(output.getAll().split("Clients cannot send messages on this socket", -1))
					.as("log lines for one batch").hasSize(2);
		}
	}

	@Test
	void theFrontendStillGetsEveryBroadcast() throws Exception {
		// The frames the live frontend sends (stomp.js over SockJS, heartbeats every 5 s).
		try (Stomp viewer = connect(headers("accept-version", "1.2,1.1,1.0", "heart-beat", "5000,5000"))) {
			viewer.subscribe("sub-0", "/topic/pixels");
			viewer.subscribe("sub-1", "/topic/fill");
			awaitSubscription(viewer, "/topic/pixels");
			awaitSubscription(viewer, "/topic/fill");

			messagingTemplate.convertAndSend("/topic/pixels", new PixelDto(3, 4, "abcdef"));
			assertThat(viewer.bodiesUntil("abcdef")).containsExactly("{\"x\":3,\"y\":4,\"color\":\"abcdef\"}");

			messagingTemplate.convertAndSend("/topic/fill", new FillDto(0, 0, 9, 9, "fedcba", null));
			assertThat(viewer.bodiesUntil("fedcba"))
					.containsExactly("{\"startX\":0,\"startY\":0,\"endX\":9,\"endY\":9,\"color\":\"fedcba\",\"key\":null}");

			viewer.heartbeat();
			viewer.send("UNSUBSCRIBE", headers("id", "sub-1"), "");
			viewer.send("DISCONNECT", headers("receipt", "close-2"), "");
			Frame receipt = viewer.poll(10, SECONDS);
			assertThat(receipt).isNotNull();
			assertThat(receipt.command()).isEqualTo("RECEIPT");
			assertThat(receipt.headers()).containsEntry("receipt-id", "close-2");
		}
	}

	/** SUBSCRIBE is handled on another thread: broadcast until the viewer hears it. */
	private void awaitSubscription(Stomp viewer, String destination) throws InterruptedException {
		for (int i = 0; i < 50; i++) {
			messagingTemplate.convertAndSend(destination, Map.of("warmup", i));
			Frame frame = viewer.poll(200, MILLISECONDS);
			if (frame != null) {
				assertThat(frame.command()).isEqualTo("MESSAGE");
				assertThat(frame.body()).contains("warmup");
				return;
			}
		}
		fail("never subscribed to " + destination);
	}

	private Stomp connect() throws Exception {
		return connect(headers("accept-version", "1.2", "heart-beat", "0,0"));
	}

	private Stomp connect(Map<String, String> connectHeaders) throws Exception {
		Stomp stomp = new Stomp();
		stomp.socket = http.newWebSocketBuilder().header("Origin", FRONTEND)
				.buildAsync(URI.create("ws://localhost:" + port + "/rplace/websocket"), stomp).get(10, SECONDS);
		stomp.send("CONNECT", connectHeaders, "");
		Frame connected = stomp.poll(10, SECONDS);
		assertThat(connected).isNotNull();
		assertThat(connected.command()).isEqualTo("CONNECTED");
		return stomp;
	}

	private static Map<String, String> headers(String... namesAndValues) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (int i = 0; i < namesAndValues.length; i += 2) {
			headers.put(namesAndValues[i], namesAndValues[i + 1]);
		}
		return headers;
	}

	private record Frame(String command, Map<String, String> headers, String body) {
	}

	/** A STOMP client written frame by frame: it sends whatever a hand-written client could. */
	private static final class Stomp implements WebSocket.Listener, AutoCloseable {

		final CompletableFuture<Integer> closed = new CompletableFuture<>();

		private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();

		private final StompDecoder decoder = new StompDecoder();

		private final StringBuilder text = new StringBuilder();

		WebSocket socket;

		void subscribe(String id, String destination) {
			send("SUBSCRIBE", headers("id", id, "destination", destination), "");
		}

		void send(String command, Map<String, String> headers, String body) {
			StringBuilder frame = new StringBuilder(command).append('\n');
			headers.forEach((name, value) -> frame.append(name).append(':').append(value).append('\n'));
			frame.append('\n').append(body).append('\0');
			socket.sendText(frame, true).join();
		}

		void heartbeat() {
			socket.sendText("\n", true).join();
		}

		Frame poll(long timeout, TimeUnit unit) throws InterruptedException {
			return frames.poll(timeout, unit);
		}

		/** The bodies of the messages that arrive until one contains the marker, warm-ups left out. */
		List<String> bodiesUntil(String marker) throws InterruptedException {
			List<String> bodies = new ArrayList<>();
			while (true) {
				Frame frame = poll(10, SECONDS);
				if (frame == null) {
					fail("no message containing " + marker + "; received " + bodies);
				}
				if (frame.body().contains("warmup")) {
					continue;
				}
				bodies.add(frame.body());
				if (frame.body().contains(marker)) {
					return bodies;
				}
			}
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			text.append(data);
			if (last) {
				ByteBuffer bytes = ByteBuffer.wrap(text.toString().getBytes(StandardCharsets.UTF_8));
				text.setLength(0);
				for (Message<byte[]> message : decoder.decode(bytes)) {
					StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
					if (accessor.isHeartbeat()) {
						continue;
					}
					Map<String, String> headers = new LinkedHashMap<>();
					accessor.toNativeHeaderMap().forEach((name, values) -> headers.put(name, values.get(0)));
					frames.add(new Frame(accessor.getCommand().name(), headers,
							new String(message.getPayload(), StandardCharsets.UTF_8)));
				}
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			closed.complete(statusCode);
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			closed.completeExceptionally(error);
		}

		@Override
		public void close() {
			socket.abort();
		}

	}

}
