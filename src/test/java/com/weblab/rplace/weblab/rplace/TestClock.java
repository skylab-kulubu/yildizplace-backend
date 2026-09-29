package com.weblab.rplace.weblab.rplace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** The application's clock in tests: the real time, moved forward when a test says so. */
final class TestClock extends Clock {

	private volatile Duration offset = Duration.ZERO;

	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		@Primary
		TestClock testClock() {
			return new TestClock();
		}
	}

	void advance(Duration duration) {
		offset = offset.plus(duration);
	}

	void reset() {
		offset = Duration.ZERO;
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(offset);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
