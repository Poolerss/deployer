package com.deployer.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deployer")
public record DeployerProperties(
		Path workDir,
		Duration startupTimeout,
		int panelPort,
		Auth auth
) {
	public record Auth(String username, String password) {
		public Auth {
			if (username == null || username.isBlank()) {
				username = "poolerss";
			}
			if (password == null || password.isBlank()) {
				password = "pool1987";
			}
		}
	}

	public DeployerProperties {
		if (workDir == null) {
			workDir = Path.of("data");
		}
		if (startupTimeout == null) {
			startupTimeout = Duration.ofSeconds(90);
		}
		if (panelPort <= 0) {
			panelPort = 8080;
		}
		if (auth == null) {
			auth = new Auth("poolerss", "pool1987");
		}
	}
}
