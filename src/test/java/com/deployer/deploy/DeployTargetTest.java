package com.deployer.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeployTargetTest {

	@Test
	void parsesHostPortAndPath() {
		DeployTarget target = DeployTarget.parse("http://localhost:9090/shop", 8080);
		assertEquals(9090, target.port());
		assertEquals("/shop", target.contextPath());
		assertEquals("http://localhost:9090/shop", target.publicUrl());
	}

	@Test
	void acceptsUrlWithoutScheme() {
		DeployTarget target = DeployTarget.parse("127.0.0.1:9091", 8080);
		assertEquals(9091, target.port());
		assertEquals("", target.contextPath());
	}

	@Test
	void rejectsPanelPort() {
		IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> DeployTarget.parse("http://localhost:8080", 8080));
		assertEquals("Порт 8080 занят панелью деплоя. Выберите другой порт", error.getMessage());
	}

	@Test
	void requiresExplicitPort() {
		assertThrows(IllegalArgumentException.class, () -> DeployTarget.parse("http://localhost/app", 8080));
	}
}
