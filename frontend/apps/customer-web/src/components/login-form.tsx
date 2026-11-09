"use client";

import Link from "next/link";
import { useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import { apiWrite } from "../lib/api";

export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await apiWrite("/auth/login", "POST", { email, password });
      router.replace("/account");
      router.refresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to sign in.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <label className="field-label" htmlFor="login-email">Email address</label>
      <input id="login-email" className="field-input" type="email" autoComplete="email"
        value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={254} />
      <label className="field-label" htmlFor="login-password">Password</label>
      <input id="login-password" className="field-input" type="password" autoComplete="current-password"
        value={password} onChange={(event) => setPassword(event.target.value)} required maxLength={72} />
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <button className="button auth-submit" type="submit" disabled={busy}>
        {busy ? "Signing in…" : "Sign in securely"}
      </button>
      <p className="form-footnote">New to Suabhi? <Link href="/register">Create your account</Link></p>
    </form>
  );
}
