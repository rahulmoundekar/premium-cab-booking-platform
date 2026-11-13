const cards = [
  ["Fleet catalogue", "Categories, models, vehicle configurations and documents"],
  ["Driver onboarding", "Identity, licence and verification workflow"],
  ["Operations", "Bookings, assignment and dispatch workspace"],
  ["Commercials", "Fare plans, invoices, payments and refunds"]
];
export default function AdminHome() {
  return <main className="shell">
    <header className="topbar"><a className="brand" href="#">SUABHI<span>.</span> OPS</a><span className="eyebrow">Operations workspace</span></header>
    <section className="hero"><p className="eyebrow">Control centre</p><h1>Fleet operations, with clarity.</h1><p className="lead">The independent operations console for managing vehicles, drivers, service coverage and the booking lifecycle.</p></section>
    <section className="grid">{cards.map(([title, detail], index) => <article className="panel" key={title}><div className="metric">WORKSPACE 0{index + 1}</div><h3>{title}</h3><p>{detail}</p></article>)}</section>
    <footer className="footer">Admin workspace shell · Authentication and operational modules are not enabled in Phase 1.</footer>
  </main>;
}
