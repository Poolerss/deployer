import { login } from "./api";
import { FormEvent, useState } from "react";

type Props = {
  onSuccess: () => void;
};

export default function Login({ onSuccess }: Props) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(username, password);
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось войти");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="shell login-shell">
      <form className="panel login-panel" onSubmit={onSubmit}>
        <p className="kicker">access</p>
        <h1>Deployer</h1>
        <p className="tagline">Вход в панель деплоя</p>
        <label className="field">
          <span>Логин</span>
          <input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            autoFocus
          />
        </label>
        <label className="field">
          <span>Пароль</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
        </label>
        {error ? <p className="error">{error}</p> : null}
        <div className="actions">
          <button type="submit" disabled={busy || !username || !password}>
            {busy ? "Вход…" : "Войти"}
          </button>
        </div>
      </form>
    </div>
  );
}
