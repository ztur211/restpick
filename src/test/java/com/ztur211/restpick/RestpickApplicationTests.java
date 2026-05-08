package com.ztur211.restpick;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "key=test-api-key")
class RestpickApplicationTests {

	@Test
	void contextLoads() {
	}

}
