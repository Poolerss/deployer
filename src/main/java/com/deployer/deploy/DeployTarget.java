package com.deployer.deploy;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

public record DeployTarget(URI uri, int port, String contextPath) {

	public static DeployTarget parse(String raw, int panelPort) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("Укажите URL приложения");
		}

		String value = raw.trim();
		if (!value.contains("://")) {
			value = "http://" + value;
		}

		URI uri;
		try {
			uri = URI.create(value);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Некорректный URL: " + raw);
		}

		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException("В URL должен быть хост, например http://localhost:9090");
		}
		if (uri.getPort() < 0) {
			throw new IllegalArgumentException("Укажите порт в URL, например http://localhost:9090");
		}
		if (uri.getPort() == panelPort) {
			throw new IllegalArgumentException(
					"Порт " + panelPort + " занят панелью деплоя. Выберите другой порт");
		}

		String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new IllegalArgumentException("Поддерживаются только http и https");
		}

		String path = Optional.ofNullable(uri.getRawPath()).orElse("");
		if (path.isBlank() || "/".equals(path)) {
			path = "";
		} else if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		if (!path.isEmpty() && !path.startsWith("/")) {
			path = "/" + path;
		}

		return new DeployTarget(uri, uri.getPort(), path);
	}

	public String publicUrl() {
		return uri.toString();
	}
}
