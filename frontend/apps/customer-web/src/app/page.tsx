import Link from "next/link";
import { SiteHeader } from "../components/site-header";

interface FleetCategory {
  code: string;
  name: string;
  description: string;
  minPassengers: number;
  maxPassengers: number;
  luggagePieces: number;
  luggageDescription: string;
}

async function getFleetCategories(): Promise<FleetCategory[]> {
  const apiBase = process.env.API_INTERNAL_URL
    || process.env.NEXT_PUBLIC_API_BASE_URL
    || "http://cab-booking-api:8080/api/v1";

  try {
    const response = await fetch(apiBase + "/public/fleet/categories", { cache: "no-store" });
    if (!response.ok) return [];
    return (await response.json()) as FleetCategory[];
  } catch {
    return [];
  }
}

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const categories = await getFleetCategories();

  return (
    <>
      <SiteHeader />
      <main>
        <section className="hero-section">
          <div className="shell hero-grid">
            <div className="hero-copy">
              <p className="eyebrow">PRIVATE TRAVEL · PUNE & MUMBAI</p>
              <h1>Arrive well.<br /><span>Every time.</span></h1>
              <p className="hero-lead">
                Thoughtfully arranged chauffeur-driven travel for airport transfers,
                city journeys and long-distance escapes.
              </p>
              <div className="hero-actions">
                <Link className="button" href="/register">Plan your journey <span aria-hidden="true">↗</span></Link>
                <a className="button button-outline" href="#fleet">Explore the fleet</a>
              </div>
              <div className="hero-trust">
                <span><b>01</b> Private, pre-arranged rides</span>
                <span><b>02</b> Clear journey details</span>
              </div>
            </div>
            <div className="hero-art" aria-label="Abstract visual representing a premium journey">
              <div className="art-orbit orbit-one" />
              <div className="art-orbit orbit-two" />
              <div className="art-core"><span>S</span><small>SUABHI</small></div>
              <div className="art-caption"><span>THE JOURNEY, REFINED</span><b>PUNE <i>—</i> MUMBAI</b></div>
              <div className="art-stamp">S<br /><small>PRIVATE<br />TRAVEL</small></div>
            </div>
          </div>
          <div className="hero-bottom-line" />
        </section>

        <section className="section shell" id="fleet">
          <div className="section-heading">
            <div><p className="eyebrow">CHOOSE YOUR SPACE</p><h2>A fleet for the way<br />you want to travel.</h2></div>
            <p className="section-aside">From a refined city ride to comfortable group travel, choose the space that suits your journey.</p>
          </div>
          {categories.length > 0 ? (
            <div className="fleet-grid">
              {categories.map((category, index) => (
                <article className="fleet-card" key={category.code}>
                  <div className="fleet-number">0{index + 1}</div>
                  <div className="fleet-card-line" />
                  <h3>{category.name}</h3>
                  <p>{category.description}</p>
                  <div className="fleet-specs">
                    <span><b>{category.minPassengers === category.maxPassengers
                      ? category.maxPassengers
                      : category.minPassengers + "–" + category.maxPassengers}</b> passengers</span>
                    <span><b>{category.luggagePieces}</b> bags*</span>
                  </div>
                  <small>{category.luggageDescription}</small>
                </article>
              ))}
            </div>
          ) : (
            <p className="empty-state">Our fleet details are temporarily unavailable. Please check back shortly.</p>
          )}
          <p className="section-footnote">*Luggage capacity varies by vehicle configuration and should be confirmed when booking.</p>
        </section>

        <section className="experience-section" id="experience">
          <div className="shell experience-grid">
            <div><p className="eyebrow">THE SUABHI STANDARD</p><h2>Considered details.<br /><span>Uncomplicated travel.</span></h2></div>
            <div className="experience-points">
              <div><span>01</span><div><h3>Travel that fits</h3><p>Choose a vehicle category based on your group and luggage, not guesswork.</p></div></div>
              <div><span>02</span><div><h3>One clear plan</h3><p>Keep journey details and account information together in one place.</p></div></div>
              <div><span>03</span><div><h3>Local, considered service</h3><p>Designed around Pune and Mumbai, with airport, local and outstation journeys in mind.</p></div></div>
            </div>
          </div>
        </section>

        <section className="service-section shell" id="service">
          <p className="eyebrow">WHERE WE TRAVEL</p><h2>Rooted in the west.<br /><span>Ready for the road.</span></h2>
          <div className="service-cities"><div><b>PUNE</b><span>Local · Airport · Outstation</span></div><div><b>MUMBAI</b><span>Local · Airport · Outstation</span></div></div>
          <div className="service-cta"><p>Make your next journey a little more considered.</p><Link className="button" href="/register">Create your Suabhi account <span aria-hidden="true">↗</span></Link></div>
        </section>
      </main>
      <footer className="site-footer"><div className="shell footer-inner"><span className="brand-name">SUABHI<span> CAB</span></span><span>Premium chauffeur-driven travel · Pune & Mumbai</span><span>© {new Date().getFullYear()} Suabhi</span></div></footer>
    </>
  );
}
