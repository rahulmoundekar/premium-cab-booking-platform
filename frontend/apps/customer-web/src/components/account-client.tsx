"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import { apiGet, apiWrite } from "../lib/api";
import type { AuthUser, CustomerProfile } from "../lib/api";

export function AccountClient() {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [profile, setProfile] = useState<CustomerProfile | null>(null);
  const [fullName, setFullName] = useState("");
  const [phoneE164, setPhoneE164] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    Promise.all([
      apiGet<AuthUser>("/auth/me"),
      apiGet<CustomerProfile>("/customer/profile"),
    ]).then(([authUser, customerProfile]) => {
      if (!active) return;
      setUser(authUser);
      setProfile(customerProfile);
      setFullName(customerProfile.fullName);
      setPhoneE164(customerProfile.phoneE164 ?? "");
    }).catch(() => {
      if (active) router.replace("/login");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [router]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const updated = await apiWrite<CustomerProfile>("/customer/profile", "PATCH", {
        fullName,
        phoneE164: phoneE164.trim(),
      });
      setProfile(updated);
      setUser((current) => current ? { ...current, fullName: updated.fullName } : current);
      setFullName(updated.fullName);
      setPhoneE164(updated.phoneE164 ?? "");
      setNotice("Your profile has been saved.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to save your profile.");
    } finally {
      setBusy(false);
    }
  }

  async function signOut() {
    setError("");
    setBusy(true);
    try {
      await apiWrite("/auth/logout", "POST");
      router.replace("/");
      router.refresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to sign out.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <p className="loading-copy">Loading your account…</p>;
  if (!user || !profile) return null;

  return (
    <section className="account-layout">
      <div className="account-intro">
        <p className="eyebrow">YOUR SUABHI ACCOUNT</p>
        <h1>Welcome back, {user.fullName.split(" ")[0]}.</h1>
        <p className="muted-copy">Your journey starts with the details you keep here.</p>
        <div className="account-status">
          <span className="status-dot" />
          <span>Account active</span>
          <span className="status-divider">·</span>
          <span>{profile.emailVerified ? "Email verified" : "Email verification pending"}</span>
        </div>
        <button className="button button-outline signout-button" onClick={signOut} disabled={busy}>
          Sign out
        </button>
        <Link className="text-link" href="/">Return to Suabhi home</Link>
      </div>

      <div className="account-card">
        <div className="card-heading">
          <div><p className="eyebrow">PERSONAL DETAILS</p><h2>Your profile</h2></div>
          <span className="profile-initial">{profile.fullName.charAt(0).toUpperCase()}</span>
        </div>
        <form className="auth-form" onSubmit={save}>
          <label className="field-label" htmlFor="profile-name">Full name</label>
          <input id="profile-name" className="field-input" value={fullName}
            onChange={(event) => setFullName(event.target.value)} required minLength={2} maxLength={160} />
          <label className="field-label" htmlFor="profile-email">Email address</label>
          <input className="field-input field-readonly" id="profile-email"
            value={profile.email} readOnly />
          <label className="field-label" htmlFor="profile-phone">Mobile number <span>International format</span></label>
          <input id="profile-phone" className="field-input" value={phoneE164}
            placeholder="+919876543210" onChange={(event) => setPhoneE164(event.target.value)} />
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          {notice ? <p className="form-success" role="status">{notice}</p> : null}
          <button className="button auth-submit" type="submit" disabled={busy}>
            {busy ? "Saving…" : "Save profile"}
          </button>
        </form>
      </div>
    </section>
  );
}
