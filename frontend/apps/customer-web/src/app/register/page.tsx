import Link from "next/link";
import { RegisterForm } from "../../components/register-form";
import { SiteHeader } from "../../components/site-header";

export default function RegisterPage() {
  return (
    <div className="auth-page">
      <SiteHeader />
      <main className="auth-main">
        <section className="auth-card">
          <Link className="auth-back" href="/">← Back to Suabhi</Link>
          <p className="eyebrow">A BETTER WAY TO ARRIVE</p>
          <h1 className="auth-title">Make yourself at home.</h1>
          <p className="auth-description">Create your account to keep your travel details close at hand.</p>
          <RegisterForm />
        </section>
      </main>
    </div>
  );
}
