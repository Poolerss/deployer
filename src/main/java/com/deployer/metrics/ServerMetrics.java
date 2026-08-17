package com.deployer.metrics;

public record ServerMetrics(
		double cpuPercent,
		double loadAverage,
		int cores,
		long memoryUsedBytes,
		long memoryTotalBytes,
		double memoryPercent,
		double netRxBytesPerSec,
		double netTxBytesPerSec
) {
}
