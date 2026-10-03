package com.weblab.rplace.weblab.rplace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

/**
 * A real Postgres 17 (the production major version) for tests that boot the
 * application. Spring Boot wires spring.datasource.* to it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgres() {
		// Data on tmpfs: nothing to keep, and faster.
		return new PostgreSQLContainer<>("postgres:17-alpine")
				.withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
	}

}
