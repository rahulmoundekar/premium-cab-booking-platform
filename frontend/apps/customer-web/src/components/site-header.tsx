import Link from "next/link";

export function SiteHeader() {
  return (
    <header className="site-header">
      <div className="shell header-inner">
        <Link className="brand-mark" href="/" aria-label="Suabhi Cab Booking home">
          <span className="brand-symbol">S</span>
          <span className="brand-name">SUABHI<span> CAB</span></span>
        </Link>
        <nav className="main-nav" aria-label="Main navigation">
          <a href="/#fleet">Our fleet</a>
          <a href="/#experience">The Suabhi standard</a>
          <a href="/#service">Service areas</a>
        </nav>
        <div className="header-actions">
          <Link className="nav-login" href="/login">Sign in</Link>
          <Link className="button button-small" href="/register">Create account</Link>
        </div>
      </div>
    </header>
  );
}
