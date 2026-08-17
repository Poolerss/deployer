package com.deployer.deploy;

import java.time.Instant;

public record DeploymentSnapshot(
		DeployStatus status,
		String jarName,
		String url,
		Long pid,
		String startedAt,
		String updatedAt,
		String error,
		String message
) {
	public static DeploymentSnapshot idle() {
		return new DeploymentSnapshot(DeployStatus.IDLE, null, null, null, null, Instant.now().toString(), null, "Слот свободен");
	}

	public DeploymentSnapshot with(
			DeployStatus newStatus,
			String newJarName,
			String newUrl,
			Long newPid,
			String newStartedAt,
			String newError,
			String newMessage
	) {
		return new DeploymentSnapshot(
				newStatus,
				newJarName,
				newUrl,
				newPid,
				newStartedAt,
				Instant.now().toString(),
				newError,
				newMessage
		);
	}
}
