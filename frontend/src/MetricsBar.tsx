import { useEffect, useRef, useState } from "react";
import { AuthError, ServerMetrics, fetchMetrics } from "./api";

type Props = {
  onAuthLost: () => void;
};

function clampPercent(value: number) {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.min(100, Math.max(0, value));
}

function formatBytes(bytes: number) {
  if (bytes >= 1024 ** 3) {
    return `${(bytes / 1024 ** 3).toFixed(1)} ГБ`;
  }
  if (bytes >= 1024 ** 2) {
    return `${(bytes / 1024 ** 2).toFixed(0)} МБ`;
  }
  return `${Math.round(bytes / 1024)} КБ`;
}

function formatRate(bytesPerSec: number) {
  if (bytesPerSec >= 1024 ** 2) {
    return `${(bytesPerSec / 1024 ** 2).toFixed(1)} МБ/с`;
  }
  if (bytesPerSec >= 1024) {
    return `${(bytesPerSec / 1024).toFixed(0)} КБ/с`;
  }
  return `${Math.max(0, Math.round(bytesPerSec))} Б/с`;
}

export default function MetricsBar({ onAuthLost }: Props) {
  const [metrics, setMetrics] = useState<ServerMetrics | null>(null);
  const onAuthLostRef = useRef(onAuthLost);
  onAuthLostRef.current = onAuthLost;

  useEffect(() => {
    let cancelled = false;

    async function tick() {
      try {
        const next = await fetchMetrics();
        if (!cancelled) {
          setMetrics(next);
        }
      } catch (err) {
        if (err instanceof AuthError) {
          onAuthLostRef.current();
        }
      }
    }

    tick();
    const id = window.setInterval(tick, 1500);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  const cpu = clampPercent(metrics?.cpuPercent ?? 0);
  const ram = clampPercent(metrics?.memoryPercent ?? 0);
  const netTotal = (metrics?.netRxBytesPerSec ?? 0) + (metrics?.netTxBytesPerSec ?? 0);
  const netBar = clampPercent(Math.min(100, (netTotal / (8 * 1024 * 1024)) * 100));

  return (
    <section className="metrics">
      <article>
        <small>cpu</small>
        <strong>{cpu.toFixed(0)}%</strong>
        <span>
          {metrics ? `${metrics.cores} ядер · load ${metrics.loadAverage < 0 ? "—" : metrics.loadAverage.toFixed(2)}` : "опрос…"}
        </span>
        <i className="meter">
          <b style={{ width: `${cpu}%` }} />
        </i>
      </article>
      <article>
        <small>память</small>
        <strong>{ram.toFixed(0)}%</strong>
        <span>
          {metrics
            ? `${formatBytes(metrics.memoryUsedBytes)} / ${formatBytes(metrics.memoryTotalBytes)}`
            : "опрос…"}
        </span>
        <i className="meter meter-ram">
          <b style={{ width: `${ram}%` }} />
        </i>
      </article>
      <article>
        <small>сеть</small>
        <strong>
          {metrics ? `${formatRate(metrics.netRxBytesPerSec + metrics.netTxBytesPerSec)}` : "—"}
        </strong>
        <span>
          {metrics
            ? `${metrics.netInterface ? metrics.netInterface + " · " : ""}↓ ${formatRate(metrics.netRxBytesPerSec)}  ↑ ${formatRate(metrics.netTxBytesPerSec)}`
            : "опрос…"}
        </span>
        <i className="meter meter-net">
          <b style={{ width: `${netBar}%` }} />
        </i>
      </article>
    </section>
  );
}
