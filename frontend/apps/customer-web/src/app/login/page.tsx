import Link from "next/link";
import { LoginForm } from "../../components/login-form";
import { SiteHeader } from "../../components/site-header";

export default function LoginPage() {
  return (
    <div className="auth-page">
      <SiteHeader />
      <main className="auth-main">
        <section className="auth-card">
          <Link className="auth-back" href="/">← Back to Suabhi</Link>
          <p className="eyebrow">WELCOME BACK</p>
          <h1 className="auth-title">Good to have you here.</h1>
          <p className="auth-description">Sign in to manage your details and keep your journeys together.</p>
          <LoginForm />
        </section>
      </main>
    </div>
  );
}
