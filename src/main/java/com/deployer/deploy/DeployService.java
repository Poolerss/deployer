package com.deployer.deploy;

import com.deployer.config.DeployerProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DeployService {

	private final DeployerProperties properties;
	private final LogHub logHub;
	private final ReentrantLock lock = new ReentrantLock();
	private final AtomicReference<DeploymentSnapshot> snapshot = new AtomicReference<>(DeploymentSnapshot.idle());
	private final AtomicReference<Process> running = new AtomicReference<>();
	private final AtomicBoolean stopRequested = new AtomicBoolean(false);

	public DeployService(DeployerProperties properties, LogHub logHub) {
		this.properties = properties;
		this.logHub = logHub;
		restoreIfAlive();
	}

	public DeploymentSnapshot snapshot() {
		return snapshot.get();
	}

	public DeploymentSnapshot deploy(MultipartFile jar, String url) {
		if (jar == null || jar.isEmpty()) {
			throw new IllegalArgumentException("Выберите JAR-файл");
		}
		String originalName = jar.getOriginalFilename() == null ? "app.jar" : jar.getOriginalFilename();
		if (!originalName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new IllegalArgumentException("Нужен файл с расширением .jar");
		}

		DeployTarget target = DeployTarget.parse(url, properties.panelPort());
		if (!lock.tryLock()) {
			throw new DeployBusyException();
		}

		Path incoming;
		try {
			incoming = storeIncoming(jar);
		} catch (RuntimeException ex) {
			lock.unlock();
			throw ex;
		}

		Thread.ofVirtual().name("deploy-job").start(() -> {
			try {
				runDeploy(incoming, originalName, target);
			} finally {
				lock.unlock();
			}
		});
		return snapshot.get();
	}

	public DeploymentSnapshot stopAndRemove() {
		if (!lock.tryLock()) {
			throw new DeployBusyException();
		}
		Thread.ofVirtual().name("undeploy-job").start(() -> {
			try {
				stopRequested.set(true);
				replaceSnapshot(DeployStatus.STOPPING, snapshot.get().jarName(), snapshot.get().url(),
						snapshot.get().pid(), snapshot.get().startedAt(), null, "Останавливаем и удаляем приложение");
				stopCurrentProcess();
				deleteCurrentSlot();
				running.set(null);
				snapshot.set(DeploymentSnapshot.idle());
				logHub.log("system", "Слот очищен. Предыдущее приложение удалено.");
				logHub.status(snapshot.get());
			} catch (Exception ex) {
				fail("Не удалось остановить приложение: " + ex.getMessage());
			} finally {
				stopRequested.set(false);
				lock.unlock();
			}
		});
		return snapshot.get();
	}

	private void runDeploy(Path incoming, String originalName, DeployTarget target) {
		stopRequested.set(false);
		logHub.clear();
		try {
			logHub.log("system", "Новый деплой: " + originalName + " → " + target.publicUrl());
			replaceSnapshot(DeployStatus.STOPPING, originalName, target.publicUrl(), null, null, null,
					"Останавливаем предыдущее приложение");

			stopCurrentProcess();
			deleteCurrentSlot();
			logHub.log("system", "Предыдущее приложение удалено");

			Path appJar = currentDir().resolve("app.jar");
			Files.createDirectories(currentDir());
			Files.move(incoming, appJar, StandardCopyOption.REPLACE_EXISTING);
			logHub.log("system", "Сохранён " + originalName);

			replaceSnapshot(DeployStatus.STARTING, originalName, target.publicUrl(), null, Instant.now().toString(), null,
					"Запускаем процесс");
			Process process = startProcess(appJar, target);
			running.set(process);
			pipeLogs(process);
			writePid(process.pid());
			replaceSnapshot(DeployStatus.STARTING, originalName, target.publicUrl(), process.pid(), Instant.now().toString(),
					null, "Ожидаем готовность на порту " + target.port());

			waitUntilReady(process, target);
			if (stopRequested.get()) {
				return;
			}
			if (!process.isAlive()) {
				fail("Процесс завершился до готовности, код " + process.exitValue());
				deleteCurrentSlot();
				return;
			}

			replaceSnapshot(DeployStatus.RUNNING, originalName, target.publicUrl(), process.pid(), Instant.now().toString(),
					null, "Приложение запущено");
			logHub.log("system", "Готово. Откройте " + target.publicUrl());
			watchProcess(process);
		} catch (Exception ex) {
			fail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			stopCurrentProcess();
			deleteCurrentSlot();
		} finally {
			try {
				Files.deleteIfExists(incoming);
			} catch (IOException ignored) {
				// incoming already moved or missing
			}
		}
	}

	private void waitUntilReady(Process process, DeployTarget target) throws InterruptedException {
		Instant deadline = Instant.now().plus(properties.startupTimeout());
		Instant nextPing = Instant.now();
		while (Instant.now().isBefore(deadline)) {
			if (stopRequested.get()) {
				return;
			}
			if (!process.isAlive()) {
				return;
			}
			if (portOpen(target.port())) {
				logHub.log("system", "Порт " + target.port() + " принял соединение");
				return;
			}
			if (!Instant.now().isBefore(nextPing)) {
				long left = Duration.between(Instant.now(), deadline).toSeconds();
				logHub.log("system", "Ждём порт " + target.port() + "… осталось " + left + " с");
				nextPing = Instant.now().plusSeconds(5);
			}
			Thread.sleep(400);
		}
		throw new IllegalStateException(
				"Приложение не открыло порт " + target.port() + " за " + properties.startupTimeout().toSeconds() + " с");
	}

	private boolean portOpen(int port) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
			return true;
		} catch (IOException ex) {
			return false;
		}
	}

	private Process startProcess(Path jar, DeployTarget target) throws IOException {
		List<String> command = new ArrayList<>();
		command.add(javaBinary().toString());
		command.add("-jar");
		command.add(jar.toAbsolutePath().toString());
		command.add("--server.port=" + target.port());
		if (!target.contextPath().isEmpty()) {
			command.add("--server.servlet.context-path=" + target.contextPath());
			command.add("--server.webflux.base-path=" + target.contextPath());
		}
		logHub.log("system", "$ " + String.join(" ", command));
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(currentDir().toFile());
		builder.redirectErrorStream(true);
		return builder.start();
	}

	private void pipeLogs(Process process) {
		Thread.ofVirtual().name("app-stdout").start(() -> {
			try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
				String line;
				while ((line = reader.readLine()) != null) {
					logHub.log("stdout", line);
				}
			} catch (IOException ignored) {
				// process closed stdout
			}
		});
	}

	private void watchProcess(Process process) {
		Thread.ofVirtual().name("app-watch").start(() -> {
			try {
				int code = process.waitFor();
				if (stopRequested.get()) {
					return;
				}
				DeploymentSnapshot current = snapshot.get();
				if (current.status() == DeployStatus.RUNNING && Long.valueOf(process.pid()).equals(current.pid())) {
					fail("Приложение остановилось, код " + code);
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});
	}

	private void stopCurrentProcess() {
		Process process = running.get();
		if (process != null && process.isAlive()) {
			logHub.log("system", "Останавливаем PID " + process.pid());
			destroyHandle(process.toHandle());
		} else {
			readPid().ifPresent(pid -> ProcessHandle.of(pid).ifPresent(handle -> {
				if (handle.isAlive()) {
					logHub.log("system", "Останавливаем сохранённый PID " + pid);
					destroyHandle(handle);
				}
			}));
		}
		running.set(null);
	}

	private void destroyHandle(ProcessHandle handle) {
		handle.descendants().forEach(child -> child.destroy());
		handle.destroy();
		try {
			handle.onExit().orTimeout(8, TimeUnit.SECONDS).join();
		} catch (Exception ignored) {
			// fall through to force kill
		}
		if (handle.isAlive()) {
			logHub.log("system", "Принудительное завершение PID " + handle.pid());
			handle.descendants().forEach(ProcessHandle::destroyForcibly);
			handle.destroyForcibly();
			try {
				handle.onExit().orTimeout(5, TimeUnit.SECONDS).join();
			} catch (Exception ignored) {
				// best effort
			}
		}
		if (!handle.isAlive()) {
			logHub.log("system", "Процесс " + handle.pid() + " завершён");
		}
	}

	private void deleteCurrentSlot() {
		Path dir = currentDir();
		if (!Files.exists(dir)) {
			return;
		}
		try (var files = Files.walk(dir)) {
			files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ex) {
					logHub.log("system", "Не удалось удалить " + path.getFileName() + ": " + ex.getMessage());
				}
			});
		} catch (IOException ex) {
			logHub.log("system", "Очистка слота: " + ex.getMessage());
		}
	}

	private Path storeIncoming(MultipartFile jar) {
		try {
			Files.createDirectories(properties.workDir());
			Path incoming = properties.workDir().resolve("incoming.jar");
			Files.deleteIfExists(incoming);
			jar.transferTo(incoming.toAbsolutePath());
			return incoming;
		} catch (IOException ex) {
			throw new IllegalStateException("Не удалось сохранить JAR: " + ex.getMessage());
		}
	}

	private void writePid(long pid) {
		try {
			Files.createDirectories(currentDir());
			Files.writeString(pidFile(), Long.toString(pid), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			logHub.log("system", "Не удалось записать PID: " + ex.getMessage());
		}
	}

	private java.util.Optional<Long> readPid() {
		try {
			if (!Files.exists(pidFile())) {
				return java.util.Optional.empty();
			}
			String raw = Files.readString(pidFile(), StandardCharsets.UTF_8).trim();
			return java.util.Optional.of(Long.parseLong(raw));
		} catch (Exception ex) {
			return java.util.Optional.empty();
		}
	}

	private void restoreIfAlive() {
		readPid().flatMap(ProcessHandle::of).filter(ProcessHandle::isAlive).ifPresent(handle -> {
			snapshot.set(new DeploymentSnapshot(
					DeployStatus.RUNNING,
					"app.jar",
					null,
					handle.pid(),
					null,
					Instant.now().toString(),
					null,
					"Восстановлен запущенный процесс"));
			logHub.log("system", "Найден живой процесс PID " + handle.pid());
			logHub.status(snapshot.get());
		});
	}

	private void fail(String message) {
		DeploymentSnapshot current = snapshot.get();
		replaceSnapshot(DeployStatus.FAILED, current.jarName(), current.url(), current.pid(), current.startedAt(),
				message, message);
		logHub.log("system", "Ошибка: " + message);
	}

	private void replaceSnapshot(
			DeployStatus status,
			String jarName,
			String url,
			Long pid,
			String startedAt,
			String error,
			String message
	) {
		DeploymentSnapshot next = snapshot.get().with(status, jarName, url, pid, startedAt, error, message);
		snapshot.set(next);
		logHub.status(next);
	}

	private Path currentDir() {
		return properties.workDir().resolve("current");
	}

	private Path pidFile() {
		return currentDir().resolve("app.pid");
	}

	private Path javaBinary() {
		String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
				? "java.exe"
				: "java";
		return Path.of(System.getProperty("java.home"), "bin", name);
	}
}
