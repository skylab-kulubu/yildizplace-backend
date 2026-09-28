package com.weblab.rplace.weblab.rplace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real Postgres 17 (the production major version) for tests that boot the
 * application. Spring Boot wires spring.datasource.* to it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgres() {
		return new PostgreSQLContainer<>("postgres:17-alpine");
	}

}
