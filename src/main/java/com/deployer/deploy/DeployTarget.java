package com.deployer.deploy;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record DeployTarget(String publicUrl, int port, String contextPath) {

	private static final Pattern PATH_CHARS = Pattern.compile("^[a-zA-Z0-9/_\\-]*$");

	public static DeployTarget parse(String raw, String publicHost, String scheme, int appPort, int panelPort) {
		if (publicHost == null || publicHost.isBlank()) {
			throw new IllegalArgumentException("Не удалось определить хост сервера");
		}
		if (appPort <= 0) {
			throw new IllegalArgumentException("Некорректный порт приложения");
		}
		if (appPort == panelPort) {
			throw new IllegalArgumentException(
					"Порт " + panelPort + " занят панелью деплоя. Задайте другой deployer.app-port");
		}

		String path = extractPath(raw);
		String normalizedScheme = scheme == null || scheme.isBlank() ? "http" : scheme.toLowerCase(Locale.ROOT);
		if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
			normalizedScheme = "http";
		}
		String url = normalizedScheme + "://" + publicHost + ":" + appPort + path;
		return new DeployTarget(url, appPort, path);
	}

	static String extractPath(String raw) {
		if (raw == null || raw.isBlank() || "/".equals(raw.trim())) {
			return "";
		}
		String value = raw.trim();
		String path;
		if (value.startsWith("/")) {
			path = value;
		} else if (value.contains("://") || looksLikeHost(value)) {
			if (!value.contains("://")) {
				value = "http://" + value;
			}
			URI uri;
			try {
				uri = URI.create(value);
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("Некорректный путь: " + raw);
			}
			path = Optional.ofNullable(uri.getRawPath()).orElse("");
		} else {
			path = "/" + value;
		}

		if (path.isBlank() || "/".equals(path)) {
			return "";
		}
		if (path.endsWith("/") && path.length() > 1) {
			path = path.substring(0, path.length() - 1);
		}
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		if (path.contains("..") || !PATH_CHARS.matcher(path).matches()) {
			throw new IllegalArgumentException("Путь может содержать только буквы, цифры, / и -");
		}
		return path;
	}

	private static boolean looksLikeHost(String value) {
		return value.contains(":") || value.contains(".");
	}
}
