package com.deployer.deploy;

import java.time.Instant;

public record LogEvent(String type, String stream, String text, String ts, DeploymentSnapshot snapshot) {

	public static LogEvent log(String stream, String text) {
		return new LogEvent("log", stream, text, Instant.now().toString(), null);
	}

	public static LogEvent status(DeploymentSnapshot snapshot) {
		return new LogEvent("status", "system", snapshot.message(), Instant.now().toString(), snapshot);
	}
}
