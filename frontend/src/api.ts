export type DeployStatus = "IDLE" | "STOPPING" | "STARTING" | "RUNNING" | "FAILED";

export type DeploymentSnapshot = {
  status: DeployStatus;
  jarName: string | null;
  url: string | null;
  pid: number | null;
  startedAt: string | null;
  updatedAt: string | null;
  error: string | null;
  message: string | null;
};

export type LogEvent = {
  type: "log" | "status";
  stream: "system" | "stdout";
  text: string;
  ts: string;
  snapshot: DeploymentSnapshot | null;
};

export type ServerMetrics = {
  cpuPercent: number;
  loadAverage: number;
  cores: number;
  memoryUsedBytes: number;
  memoryTotalBytes: number;
  memoryPercent: number;
  netRxBytesPerSec: number;
  netTxBytesPerSec: number;
};

export class AuthError extends Error {
  constructor(message = "Требуется вход") {
    super(message);
    this.name = "AuthError";
  }
}

const jsonHeaders = { "Content-Type": "application/json" };

async function readError(response: Response, fallback: string) {
  const payload = await response.json().catch(() => ({}));
  return typeof payload.error === "string" ? payload.error : fallback;
}

async function ensureOk(response: Response, fallback: string) {
  if (response.status === 401) {
    throw new AuthError(await readError(response, "Требуется вход"));
  }
  if (!response.ok) {
    throw new Error(await readError(response, fallback));
  }
  return response;
}

export async function login(username: string, password: string): Promise<void> {
  const response = await fetch("/api/login", {
    method: "POST",
    headers: jsonHeaders,
    credentials: "include",
    body: JSON.stringify({ username, password }),
  });
  await ensureOk(response, "Не удалось войти");
}

export async function logout(): Promise<void> {
  await fetch("/api/logout", { method: "POST", credentials: "include" });
}

export async function fetchSnapshot(): Promise<DeploymentSnapshot> {
  const response = await fetch("/api/deploy", { credentials: "include" });
  await ensureOk(response, "Не удалось получить статус");
  return response.json();
}

export async function fetchMetrics(): Promise<ServerMetrics> {
  const response = await fetch("/api/metrics", { credentials: "include" });
  await ensureOk(response, "Не удалось получить метрики");
  return response.json();
}

export async function startDeploy(file: File, url: string): Promise<DeploymentSnapshot> {
  const body = new FormData();
  body.append("jar", file);
  body.append("url", url);
  const response = await fetch("/api/deploy", { method: "POST", body, credentials: "include" });
  const payload = await response.json().catch(() => ({}));
  if (response.status === 401) {
    throw new AuthError(payload.error ?? "Требуется вход");
  }
  if (!response.ok) {
    throw new Error(payload.error ?? "Деплой не запустился");
  }
  return payload;
}

export async function stopDeploy(): Promise<void> {
  const response = await fetch("/api/deploy", { method: "DELETE", credentials: "include" });
  const payload = await response.json().catch(() => ({}));
  if (response.status === 401) {
    throw new AuthError(payload.error ?? "Требуется вход");
  }
  if (!response.ok) {
    throw new Error(payload.error ?? "Не удалось остановить приложение");
  }
}
