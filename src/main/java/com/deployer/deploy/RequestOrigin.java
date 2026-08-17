package com.deployer.deploy;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestOrigin {

	private RequestOrigin() {
	}

	public static String host(HttpServletRequest request, String configured) {
		if (configured != null && !configured.isBlank()) {
			return configured.trim();
		}
		String forwarded = request.getHeader("X-Forwarded-Host");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim().split(":")[0];
		}
		return request.getServerName();
	}

	public static String scheme(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-Proto");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getScheme();
	}
}
