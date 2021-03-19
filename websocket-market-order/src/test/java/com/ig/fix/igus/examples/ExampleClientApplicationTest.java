package com.ig.fix.igus.examples;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(
		properties = {
				"URL=ws://localhost",
				"IG_USERNAME=bogous",
				"IG_PASSWORD=bogous",
				"IG_ACCOUNT=account1"
				
		})
class ExampleClientApplicationTest {

	@Test
	void contextLoads() {
	}

}
