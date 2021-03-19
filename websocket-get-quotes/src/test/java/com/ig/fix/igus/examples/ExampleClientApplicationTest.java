package com.ig.fix.igus.examples;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(
		properties = {
				"URL=ws://localhost",
				"IG_USERNAME=bogous",
				"IG_PASSWORD=bogous"
		})
class ExampleClientApplicationTest {

	@Test
	void contextLoads() {
	}

}
