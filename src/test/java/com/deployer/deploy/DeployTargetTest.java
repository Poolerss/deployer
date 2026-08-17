package com.deployer.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeployTargetTest {

	@Test
	void buildsPublicUrlFromPathOnly() {
		DeployTarget target = DeployTarget.parse("/testurl", "93.177.116.80", "http", 9090, 18080);
		assertEquals(9090, target.port());
		assertEquals("/testurl", target.contextPath());
		assertEquals("http://93.177.116.80:9090/testurl", target.publicUrl());
	}

	@Test
	void treatsBareNameAsPath() {
		DeployTarget target = DeployTarget.parse("shop", "example.com", "http", 9090, 8080);
		assertEquals("/shop", target.contextPath());
		assertEquals("http://example.com:9090/shop", target.publicUrl());
	}

	@Test
	void replacesLocalhostWithServerHost() {
		DeployTarget target = DeployTarget.parse("http://localhost:9090/shop", "93.177.116.80", "http", 9090, 18080);
		assertEquals("/shop", target.contextPath());
		assertEquals("http://93.177.116.80:9090/shop", target.publicUrl());
	}

	@Test
	void rootPathStaysEmpty() {
		DeployTarget target = DeployTarget.parse("/", "93.177.116.80", "http", 9090, 18080);
		assertEquals("", target.contextPath());
		assertEquals("http://93.177.116.80:9090", target.publicUrl());
	}

	@Test
	void rejectsPanelPort() {
		IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> DeployTarget.parse("/app", "localhost", "http", 8080, 8080));
		assertEquals("Порт 8080 занят панелью деплоя. Задайте другой deployer.app-port", error.getMessage());
	}

	@Test
	void rejectsDotDotPath() {
		assertThrows(IllegalArgumentException.class, () -> DeployTarget.parse("/../secret", "h", "http", 9090, 8080));
	}
}
