package com.deployer.metrics;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

	private static final Path PROC_STAT = Path.of("/proc/stat");
	private static final Path PROC_MEMINFO = Path.of("/proc/meminfo");
	private static final Path PROC_NET_DEV = Path.of("/proc/net/dev");
	private static final Path PROC_NET_ROUTE = Path.of("/proc/net/route");

	private final OperatingSystemMXBean os =
			(OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

	private long lastCpuIdle;
	private long lastCpuTotal;
	private double cpuPercent;

	private long lastRxBytes;
	private long lastTxBytes;
	private long lastSampleNanos;
	private double rxBytesPerSec;
	private double txBytesPerSec;
	private String netInterface = "";

	public synchronized ServerMetrics snapshot() {
		refreshCpu();
		refreshNetwork();
		long[] memory = readMemory();
		long total = memory[0];
		long used = memory[1];
		return new ServerMetrics(
				cpuPercent,
				os.getSystemLoadAverage(),
				os.getAvailableProcessors(),
				used,
				total,
				used * 100.0 / total,
				rxBytesPerSec,
				txBytesPerSec,
				netInterface.isBlank() ? null : netInterface
		);
	}

	private void refreshCpu() {
		long[] sample = readCpuTicks();
		if (sample != null) {
			long idle = sample[0];
			long total = sample[1];
			if (lastCpuTotal > 0) {
				long idleDelta = idle - lastCpuIdle;
				long totalDelta = total - lastCpuTotal;
				if (totalDelta > 0) {
					cpuPercent = Math.max(0, Math.min(100, (1.0 - (idleDelta / (double) totalDelta)) * 100));
				}
			}
			lastCpuIdle = idle;
			lastCpuTotal = total;
			return;
		}
		double load = os.getCpuLoad();
		cpuPercent = load < 0 ? 0 : load * 100;
	}

	private long[] readCpuTicks() {
		if (!Files.isRegularFile(PROC_STAT)) {
			return null;
		}
		try {
			for (String line : Files.readAllLines(PROC_STAT)) {
				if (!line.startsWith("cpu ")) {
					continue;
				}
				String[] parts = line.trim().split("\\s+");
				if (parts.length < 5) {
					return null;
				}
				long user = Long.parseLong(parts[1]);
				long nice = Long.parseLong(parts[2]);
				long system = Long.parseLong(parts[3]);
				long idle = Long.parseLong(parts[4]);
				long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
				long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
				long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
				long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;
				long idleAll = idle + iowait;
				long total = user + nice + system + idle + iowait + irq + softirq + steal;
				return new long[] {idleAll, total};
			}
		} catch (IOException | NumberFormatException ignored) {
			return null;
		}
		return null;
	}

	private long[] readMemory() {
		Map<String, Long> values = readMeminfo();
		if (!values.isEmpty()) {
			long totalKb = values.getOrDefault("MemTotal", 0L);
			long availableKb = values.getOrDefault("MemAvailable", values.getOrDefault("MemFree", 0L));
			long total = Math.max(totalKb * 1024, 1);
			long used = Math.max(total - availableKb * 1024, 0);
			return new long[] {total, used};
		}
		long total = Math.max(os.getTotalMemorySize(), 1);
		long free = os.getFreeMemorySize();
		return new long[] {total, Math.max(total - free, 0)};
	}

	private Map<String, Long> readMeminfo() {
		Map<String, Long> values = new HashMap<>();
		if (!Files.isRegularFile(PROC_MEMINFO)) {
			return values;
		}
		try {
			for (String line : Files.readAllLines(PROC_MEMINFO)) {
				int colon = line.indexOf(':');
				if (colon < 0) {
					continue;
				}
				String key = line.substring(0, colon).trim();
				String rest = line.substring(colon + 1).trim().toLowerCase(Locale.ROOT).replace("kb", "").trim();
				int space = rest.indexOf(' ');
				if (space > 0) {
					rest = rest.substring(0, space);
				}
				values.put(key, Long.parseLong(rest));
			}
		} catch (IOException | NumberFormatException ignored) {
			values.clear();
		}
		return values;
	}

	private void refreshNetwork() {
		String iface = readDefaultInterface();
		long[] counters = readNetworkCounters(iface);
		long now = System.nanoTime();
		if (counters != null && lastSampleNanos > 0) {
			double seconds = (now - lastSampleNanos) / 1_000_000_000.0;
			if (seconds > 0.2) {
				rxBytesPerSec = Math.max(0, (counters[0] - lastRxBytes) / seconds);
				txBytesPerSec = Math.max(0, (counters[1] - lastTxBytes) / seconds);
			}
		}
		if (counters != null) {
			lastRxBytes = counters[0];
			lastTxBytes = counters[1];
			lastSampleNanos = now;
			netInterface = iface == null ? "" : iface;
		}
	}

	private String readDefaultInterface() {
		if (!Files.isRegularFile(PROC_NET_ROUTE)) {
			return null;
		}
		try {
			List<String> lines = Files.readAllLines(PROC_NET_ROUTE);
			for (int i = 1; i < lines.size(); i++) {
				String[] parts = lines.get(i).trim().split("\\s+");
				if (parts.length >= 2 && "00000000".equals(parts[1])) {
					return parts[0];
				}
			}
		} catch (IOException ignored) {
			return null;
		}
		return null;
	}

	private long[] readNetworkCounters(String preferred) {
		if (!Files.isRegularFile(PROC_NET_DEV)) {
			return null;
		}
		try {
			List<String> lines = Files.readAllLines(PROC_NET_DEV);
			long rx = 0;
			long tx = 0;
			boolean matched = false;
			for (String line : lines) {
				int colon = line.indexOf(':');
				if (colon < 0) {
					continue;
				}
				String iface = line.substring(0, colon).trim();
				if (iface.isEmpty() || skipInterface(iface)) {
					continue;
				}
				if (preferred != null && !preferred.equals(iface)) {
					continue;
				}
				String[] parts = line.substring(colon + 1).trim().split("\\s+");
				if (parts.length < 10) {
					continue;
				}
				rx += Long.parseLong(parts[0]);
				tx += Long.parseLong(parts[8]);
				matched = true;
			}
			if (!matched && preferred != null) {
				return readNetworkCounters(null);
			}
			return matched ? new long[] {rx, tx} : null;
		} catch (IOException | NumberFormatException ex) {
			return null;
		}
	}

	private boolean skipInterface(String iface) {
		return iface.equals("lo")
				|| iface.startsWith("docker")
				|| iface.startsWith("br-")
				|| iface.startsWith("veth")
				|| iface.startsWith("virbr")
				|| iface.startsWith("tun")
				|| iface.startsWith("wg");
	}
}
