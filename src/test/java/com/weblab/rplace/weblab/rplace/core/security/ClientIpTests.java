package com.weblab.rplace.weblab.rplace.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which address a request came from (bans, the hourly link limit, pixel logs).
 * X-Forwarded-For is believed only when the peer is the proxy in front of Place
 * (PLACE_TRUSTED_PROXY_RANGES), and then only its right end: the entry that proxy
 * wrote. Whatever a client wrote to the left of it never becomes the address.
 */
class ClientIpTests {

	private static final String TRAEFIK = "10.0.1.4";

	private final ClientIp clientIp = new ClientIp("10.0.1.0/24");

	@Test
	void withoutAProxyThePeerIsTheClient() {
		assertThat(clientIp.of(request("198.51.100.7"))).isEqualTo("198.51.100.7");
	}

	@Test
	void aForwardedForFromAPeerThatIsNotTheProxyIsIgnored() {
		assertThat(clientIp.of(request("198.51.100.7", "203.0.113.10"))).isEqualTo("198.51.100.7");
	}

	@Test
	void theProxysEntryIsTheClient() {
		assertThat(clientIp.of(request(TRAEFIK, "203.0.113.10"))).isEqualTo("203.0.113.10");
	}

	@Test
	void aForgedLeftEntryNeverBecomesTheClient() {
		assertThat(clientIp.of(request(TRAEFIK, "192.0.2.66, 203.0.113.10"))).isEqualTo("203.0.113.10");
	}

	@Test
	void proxiesOnTheRightAreSkipped() {
		assertThat(clientIp.of(request(TRAEFIK, "192.0.2.66, 203.0.113.10, 10.0.1.9"))).isEqualTo("203.0.113.10");
	}

	@Test
	void repeatedHeaderLinesAreOneChain() {
		assertThat(clientIp.of(request(TRAEFIK, "192.0.2.66", "203.0.113.10"))).isEqualTo("203.0.113.10");
	}

	@Test
	void aChainOfOnlyProxiesOrNothingFallsBackToThePeer() {
		assertThat(clientIp.of(request(TRAEFIK, "10.0.1.9"))).isEqualTo(TRAEFIK);
		assertThat(clientIp.of(request(TRAEFIK, " , "))).isEqualTo(TRAEFIK);
		assertThat(clientIp.of(request(TRAEFIK))).isEqualTo(TRAEFIK);
	}

	@Test
	void anEntryThatIsNotAnAddressBreaksTheChain() {
		assertThat(clientIp.of(request(TRAEFIK, "203.0.113.10, unknown"))).isEqualTo(TRAEFIK);
		assertThat(clientIp.of(request(TRAEFIK, "example.com"))).isEqualTo(TRAEFIK);
		assertThat(clientIp.of(request(TRAEFIK, "203.0.113.10, 1.2.3"))).isEqualTo(TRAEFIK);
		assertThat(clientIp.of(request(TRAEFIK, "203.0.113.10, 256.1.1.1"))).isEqualTo(TRAEFIK);
		assertThat(clientIp.of(request(TRAEFIK, "203.0.113.10, fe80::1%eth0"))).isEqualTo(TRAEFIK);
		assertThat(clientIp.of(request(TRAEFIK, "ffff:1"))).isEqualTo(TRAEFIK);
		// Garbage further left than the proxy's entry does not matter.
		assertThat(clientIp.of(request(TRAEFIK, "<script>, 203.0.113.10"))).isEqualTo("203.0.113.10");
	}

	@Test
	void addressesAreStoredInOneSpelling() {
		assertThat(clientIp.of(request(TRAEFIK, "203.0.113.10:51234"))).isEqualTo("203.0.113.10");
		assertThat(clientIp.of(request(TRAEFIK, "[2001:DB8:0:0:0:0:0:1]:443"))).isEqualTo("2001:db8::1");
		assertThat(clientIp.of(request(TRAEFIK, "[2001:db8::1]"))).isEqualTo("2001:db8::1");
		assertThat(clientIp.of(request(TRAEFIK, "2001:0db8:0000:0001:0000:0000:0000:0001"))).isEqualTo("2001:db8:0:1::1");
		assertThat(clientIp.of(request(TRAEFIK, "2001:db8:0:0:1:0:0:1"))).isEqualTo("2001:db8::1:0:0:1");
		assertThat(clientIp.of(request(TRAEFIK, "2001:db8:1:1:1:1:0:1"))).isEqualTo("2001:db8:1:1:1:1:0:1");
		assertThat(clientIp.of(request(TRAEFIK, "::ffff:203.0.113.10"))).isEqualTo("203.0.113.10");
		assertThat(clientIp.of(request("0:0:0:0:0:0:0:1"))).isEqualTo("::1");
	}

	@Test
	void anIpv4MappedPeerInTheRangeIsTheProxy() {
		assertThat(clientIp.of(request("::ffff:10.0.1.4", "203.0.113.10"))).isEqualTo("203.0.113.10");
	}

	@Test
	void rangesAreCidrsOrSingleAddresses() {
		ClientIp several = new ClientIp(" 10.0.1.0/24 , 172.18.0.5, fd00::/8 ");

		assertThat(several.of(request("172.18.0.5", "203.0.113.10"))).isEqualTo("203.0.113.10");
		assertThat(several.of(request("172.18.0.6", "203.0.113.10"))).isEqualTo("172.18.0.6");
		assertThat(several.of(request("fd12::1", "203.0.113.10"))).isEqualTo("203.0.113.10");
		assertThat(several.of(request("10.0.2.1", "203.0.113.10"))).isEqualTo("10.0.2.1");
	}

	@Test
	void aBlankSettingMeansTheDefault() {
		ClientIp blank = new ClientIp("  ");

		assertThat(blank.trustedProxies()).isEqualTo(ClientIp.DEFAULT_TRUSTED_PROXY_RANGES);
		assertThat(blank.of(request(TRAEFIK, "203.0.113.10"))).isEqualTo("203.0.113.10");
	}

	@Test
	void aTypoStopsTheApplicationInsteadOfBeingSkipped() {
		assertThatThrownBy(() -> new ClientIp("10.0.1.0/24, 10.0.1.0/33")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("PLACE_TRUSTED_PROXY_RANGES");
		assertThatThrownBy(() -> new ClientIp("traefik")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ClientIp("10.0.1.0/x")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ClientIp("10.0.1/24")).isInstanceOf(IllegalArgumentException.class);
	}

	private static MockHttpServletRequest request(String peer, String... forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(peer);
		for (String line : forwardedFor) {
			request.addHeader("X-Forwarded-For", line);
		}
		return request;
	}

}
