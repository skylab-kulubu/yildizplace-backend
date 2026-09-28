package com.weblab.rplace.weblab.rplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(PostgresTestcontainer.class)
@ActiveProfiles("test")
class WeblabRplaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
