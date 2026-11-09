const fleet = [
  { code: "01", name: "Executive Sedan", detail: "Dzire · Aura", capacity: "4 passengers · 2 bags" },
  { code: "02", name: "Premium Executive SUV", detail: "Innova · Innova Crysta", capacity: "6–7 passengers · 4 bags" },
  { code: "03", name: "Sustainable Luxury Hybrid", detail: "Innova Hycross Hybrid", capacity: "6–7 passengers · 4 bags" },
  { code: "04", name: "Luxury Group Transit", detail: "Force Urbania", capacity: "9–17 reclining seats" },
  { code: "05", name: "Mega Group Coaches", detail: "32 / 45-seater premium buses", capacity: "Large groups · undercarriage luggage" }
];

export default function HomePage() {
  return <main className="shell">
    <header className="topbar">
      <a className="brand" href="#top">SUABHI<span>.</span></a>
      <nav className="nav" aria-label="Main navigation"><a href="#fleet">Our fleet</a><a href="#services">Services</a><a href="#about">About</a></nav>
      <a className="button secondary" href="#fleet">Explore fleet</a>
    </header>
    <section className="hero" id="top">
      <p className="eyebrow">Premium journeys · Pune & Mumbai</p>
      <h1>Every journey deserves a better way to travel.</h1>
      <p className="lead">Thoughtfully selected sedans, executive SUVs, luxury vans and premium coaches for airport transfers, local travel, outstation journeys and group movements.</p>
      <div className="actions"><a className="button" href="#fleet">Discover the fleet</a><a className="button secondary" href="#services">Explore services</a></div>
    </section>
    <section id="fleet"><p className="eyebrow">Travel in your class</p><h2>Our fleet categories</h2>
      <div className="grid">{fleet.map(item => <article className="panel" key={item.code}><div className="metric">CLASS {item.code}</div><h3>{item.name}</h3><p>{item.detail}</p><p style={{marginTop:12}}>{item.capacity}</p></article>)}</div>
    </section>
    <section id="services" className="grid">
      <article className="panel"><div className="metric">01 / AIRPORT</div><h3>Airport transfers</h3><p>Planned pickups and drop-offs with a clear travel itinerary.</p></article>
      <article className="panel"><div className="metric">02 / LOCAL</div><h3>Local rentals</h3><p>Flexible hourly and full-day travel options.</p></article>
      <article className="panel"><div className="metric">03 / OUTSTATION</div><h3>Outstation journeys</h3><p>One-way, round-trip and multi-day journeys.</p></article>
      <article className="panel"><div className="metric">04 / GROUP</div><h3>Group transportation</h3><p>Corporate events, weddings and destination travel.</p></article>
    </section>
    <footer className="footer" id="about">Suabhi Cab Booking · Phase 1 foundation · Pune and Mumbai operating launch</footer>
  </main>;
}
