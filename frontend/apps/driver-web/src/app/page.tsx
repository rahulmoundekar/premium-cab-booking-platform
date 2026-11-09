export default function DriverHome() {
  return <main className="shell">
    <header className="topbar"><a className="brand" href="#">SUABHI<span>.</span> DRIVER</a><span className="eyebrow">Driver workspace</span></header>
    <section className="hero"><p className="eyebrow">Your trips, at a glance</p><h1>A calmer way to manage every journey.</h1><p className="lead">The driver workspace will bring assigned trips, pickup details, trip milestones and operational updates into one secure place.</p><div className="actions"><span className="button secondary" aria-disabled="true">Driver sign-in · Coming in a later phase</span></div></section>
    <section className="grid">
      <article className="panel"><div className="metric">TRIPS</div><h3>Trip schedule</h3><p>View upcoming assignments and pickup information.</p></article>
      <article className="panel"><div className="metric">STATUS</div><h3>Availability</h3><p>Manage online, offline and on-trip status.</p></article>
      <article className="panel"><div className="metric">ROUTE</div><h3>Navigation</h3><p>Open the route and share trip progress securely.</p></article>
    </section>
    <footer className="footer">Driver PWA shell · Driver authentication and trip operations are not enabled in Phase 1.</footer>
  </main>;
}
