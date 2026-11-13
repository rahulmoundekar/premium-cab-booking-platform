"use client";

import Link from "next/link";
import { useState } from "react";
import type { FormEvent } from "react";
import { apiWrite } from "../lib/api";

export function RegisterForm() {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phoneE164, setPhoneE164] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [created, setCreated] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await apiWrite("/auth/register", "POST", {
        fullName,
        email,
        phoneE164: phoneE164.trim(),
        password,
      });
      setCreated(true);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to create your account.");
    } finally {
      setBusy(false);
    }
  }

  if (created) {
    return (
      <div className="form-success-panel">
        <p className="form-success" role="status">Your account has been created. Email verification is not enabled yet.</p>
        <p className="form-footnote">You can now <Link href="/login">sign in to your account</Link>.</p>
      </div>
    );
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <label className="field-label" htmlFor="register-name">Full name</label>
      <input id="register-name" className="field-input" autoComplete="name"
        value={fullName} onChange={(event) => setFullName(event.target.value)}
        required minLength={2} maxLength={160} />
      <label className="field-label" htmlFor="register-email">Email address</label>
      <input id="register-email" className="field-input" type="email" autoComplete="email"
        value={email} onChange={(event) => setEmail(event.target.value)}
        required maxLength={254} />
      <label className="field-label" htmlFor="register-phone">Mobile number <span>Optional, with country code</span></label>
      <input id="register-phone" className="field-input" type="tel" autoComplete="tel"
        placeholder="+919876543210" value={phoneE164}
        onChange={(event) => setPhoneE164(event.target.value)} />
      <label className="field-label" htmlFor="register-password">Password</label>
      <input id="register-password" className="field-input" type="password" autoComplete="new-password"
        value={password} onChange={(event) => setPassword(event.target.value)}
        required minLength={12} maxLength={72} />
      <p className="field-hint">Use at least 12 characters. Choose a password you do not use elsewhere.</p>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <button className="button auth-submit" type="submit" disabled={busy}>
        {busy ? "Creating account…" : "Create your account"}
      </button>
      <p className="form-footnote">Already have an account? <Link href="/login">Sign in</Link></p>
    </form>
  );
}
