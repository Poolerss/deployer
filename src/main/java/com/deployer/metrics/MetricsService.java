package com.deployer.metrics;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

	private static final Path NET_DEV = Path.of("/proc/net/dev");
	private final OperatingSystemMXBean os =
			(OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

	private long lastRxBytes;
	private long lastTxBytes;
	private long lastSampleNanos;
	private double rxBytesPerSec;
	private double txBytesPerSec;

	public MetricsService() {
		os.getCpuLoad();
	}

	public synchronized ServerMetrics snapshot() {
		refreshNetwork();
		double cpu = os.getCpuLoad();
		if (cpu < 0) {
			cpu = 0;
		}
		long total = Math.max(os.getTotalMemorySize(), 1);
		long used = Math.max(total - os.getFreeMemorySize(), 0);
		return new ServerMetrics(
				cpu * 100,
				os.getSystemLoadAverage(),
				os.getAvailableProcessors(),
				used,
				total,
				used * 100.0 / total,
				rxBytesPerSec,
				txBytesPerSec
		);
	}

	private void refreshNetwork() {
		long[] counters = readNetworkCounters();
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
		}
	}

	private long[] readNetworkCounters() {
		if (!Files.isRegularFile(NET_DEV)) {
			return null;
		}
		try {
			List<String> lines = Files.readAllLines(NET_DEV);
			long rx = 0;
			long tx = 0;
			for (String line : lines) {
				int colon = line.indexOf(':');
				if (colon < 0) {
					continue;
				}
				String iface = line.substring(0, colon).trim();
				if (iface.isEmpty() || skipInterface(iface)) {
					continue;
				}
				String[] parts = line.substring(colon + 1).trim().split("\\s+");
				if (parts.length < 10) {
					continue;
				}
				rx += Long.parseLong(parts[0]);
				tx += Long.parseLong(parts[8]);
			}
			return new long[] {rx, tx};
		} catch (IOException | NumberFormatException ex) {
			return null;
		}
	}

	private boolean skipInterface(String iface) {
		return iface.equals("lo")
				|| iface.startsWith("docker")
				|| iface.startsWith("br-")
				|| iface.startsWith("veth")
				|| iface.startsWith("virbr");
	}
}
