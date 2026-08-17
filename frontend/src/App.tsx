import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  AuthError,
  DeploymentSnapshot,
  LogEvent,
  fetchSnapshot,
  logout,
  startDeploy,
  stopDeploy,
} from "./api";
import Login from "./Login";
import "./App.css";

const EMPTY: DeploymentSnapshot = {
  status: "IDLE",
  jarName: null,
  url: null,
  pid: null,
  startedAt: null,
  updatedAt: null,
  error: null,
  message: "Слот свободен",
};

const STEPS = ["STOPPING", "STARTING", "RUNNING"] as const;

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value.slice(11, 19) || value;
  }
  return date.toLocaleTimeString("ru-RU", { hour12: false });
}

function statusLabel(status: DeploymentSnapshot["status"]) {
  switch (status) {
    case "IDLE":
      return "свободен";
    case "STOPPING":
      return "снятие";
    case "STARTING":
      return "запуск";
    case "RUNNING":
      return "в эфире";
    case "FAILED":
      return "ошибка";
  }
}

export default function App() {
  const [authed, setAuthed] = useState<boolean | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [url, setUrl] = useState("http://localhost:9090");
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<DeploymentSnapshot>(EMPTY);
  const [logs, setLogs] = useState<LogEvent[]>([]);
  const consoleRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const deploying = snapshot.status === "STOPPING" || snapshot.status === "STARTING" || busy;

  useEffect(() => {
    fetchSnapshot()
      .then((next) => {
        setSnapshot(next);
        setAuthed(true);
      })
      .catch(() => {
        setAuthed(false);
      });
  }, []);

  useEffect(() => {
    if (!authed) {
      return;
    }
    const source = new EventSource("/api/deploy/stream");
    source.onmessage = (event) => {
      const payload = JSON.parse(event.data) as LogEvent;
      if (payload.snapshot) {
        setSnapshot(payload.snapshot);
      }
      if (payload.type === "log") {
        setLogs((current) => [...current.slice(-400), payload]);
      }
    };
    source.onerror = () => {
      // браузер сам переподключит EventSource
    };
    return () => source.close();
  }, [authed]);

  useEffect(() => {
    const node = consoleRef.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [logs]);

  const activeStep = useMemo(() => {
    if (snapshot.status === "FAILED") {
      return -1;
    }
    return STEPS.indexOf(snapshot.status as (typeof STEPS)[number]);
  }, [snapshot.status]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      setError("Выберите JAR");
      return;
    }
    setError(null);
    setBusy(true);
    setLogs([]);
    try {
      const next = await startDeploy(file, url);
      setSnapshot(next);
    } catch (err) {
      if (err instanceof AuthError) {
        setAuthed(false);
        return;
      }
      setError(err instanceof Error ? err.message : "Ошибка деплоя");
    } finally {
      setBusy(false);
    }
  }

  async function onStop() {
    setError(null);
    setBusy(true);
    try {
      await stopDeploy();
    } catch (err) {
      if (err instanceof AuthError) {
        setAuthed(false);
        return;
      }
      setError(err instanceof Error ? err.message : "Не удалось остановить");
    } finally {
      setBusy(false);
    }
  }

  function takeFile(next: File | null) {
    if (!next) {
      return;
    }
    if (!next.name.toLowerCase().endsWith(".jar")) {
      setError("Нужен файл .jar");
      return;
    }
    setError(null);
    setFile(next);
  }

  async function onLogout() {
    await logout();
    setAuthed(false);
  }

  if (authed === null) {
    return (
      <div className="shell login-shell">
        <p className="tagline">Проверка сессии…</p>
      </div>
    );
  }

  if (!authed) {
    return (
      <Login
        onSuccess={() => {
          if (window.location.pathname === "/login") {
            window.history.replaceState(null, "", "/");
          }
          setAuthed(true);
          fetchSnapshot()
            .then(setSnapshot)
            .catch(() => undefined);
        }}
      />
    );
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div>
          <p className="kicker">single slot</p>
          <h1>Deployer</h1>
        </div>
        <div className="topbar-actions">
          <div className={`led status-${snapshot.status.toLowerCase()}`}>
            <span />
            {statusLabel(snapshot.status)}
          </div>
          <button type="button" className="ghost" onClick={onLogout}>
            Выйти
          </button>
        </div>
      </header>

      <p className="tagline">
        Один живой процесс. Следующий JAR останавливает предыдущий и удаляет его с диска.
      </p>

      <div className="layout">
        <form className="panel" onSubmit={onSubmit}>
          <section
            className={`drop ${dragging ? "is-drag" : ""} ${file ? "has-file" : ""}`}
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(false);
              takeFile(event.dataTransfer.files[0] ?? null);
            }}
            onClick={() => inputRef.current?.click()}
          >
            <input
              ref={inputRef}
              type="file"
              accept=".jar,application/java-archive"
              hidden
              onChange={(event) => takeFile(event.target.files?.[0] ?? null)}
            />
            <small>payload</small>
            <strong>{file ? file.name : "Выберите JAR"}</strong>
            <span>
              {file
                ? `${(file.size / (1024 * 1024)).toFixed(2)} МБ`
                : "Перетащите архив сюда или нажмите, чтобы выбрать"}
            </span>
          </section>

          <label className="field">
            <span>URL приложения</span>
            <input
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="http://localhost:9090"
              autoComplete="off"
              spellCheck={false}
            />
            <em>Порт из URL станет server.port. Панель занимает 8080.</em>
          </label>

          <ol className="steps">
            <li className={activeStep >= 0 ? "done" : ""}>Снять прошлое</li>
            <li className={activeStep >= 1 ? "done" : snapshot.status === "STARTING" ? "now" : ""}>
              Запустить процесс
            </li>
            <li className={snapshot.status === "RUNNING" ? "done" : ""}>Открыть ссылку</li>
          </ol>

          {error ? <p className="error">{error}</p> : null}
          {snapshot.status === "FAILED" && snapshot.error ? (
            <p className="error">{snapshot.error}</p>
          ) : null}

          <div className="actions">
            <button type="submit" disabled={deploying}>
              {deploying ? "Деплой…" : "Запустить деплой"}
            </button>
            <button
              type="button"
              className="ghost"
              disabled={deploying || snapshot.status === "IDLE"}
              onClick={onStop}
            >
              Удалить текущее
            </button>
          </div>

          {snapshot.status === "RUNNING" && snapshot.url ? (
            <a className="launch" href={snapshot.url} target="_blank" rel="noreferrer">
              <span>приложение готово</span>
              <strong>{snapshot.url}</strong>
              <em>перейти ↗</em>
            </a>
          ) : (
            <div className="idle-note">
              {snapshot.message ?? "После успешного старта здесь появится ссылка."}
            </div>
          )}
        </form>

        <section className="console-wrap">
          <div className="console-head">
            <span>журнал деплоя</span>
            {snapshot.pid ? <code>pid {snapshot.pid}</code> : <code>ожидание</code>}
          </div>
          <div className="console" ref={consoleRef}>
            {logs.length === 0 ? (
              <p className="placeholder">Процесс появится здесь: остановка, копирование JAR, вывод приложения.</p>
            ) : (
              logs.map((line, index) => (
                <p key={`${line.ts}-${index}`} className={`line line-${line.stream}`}>
                  <time>{formatTime(line.ts)}</time>
                  <b>{line.stream === "system" ? "sys" : "app"}</b>
                  <span>{line.text}</span>
                </p>
              ))
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
