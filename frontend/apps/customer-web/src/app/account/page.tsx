import { SiteHeader } from "../../components/site-header";
import { AccountClient } from "../../components/account-client";

export default function AccountPage() {
  return (
    <>
      <SiteHeader />
      <main><AccountClient /></main>
      <footer className="site-footer"><div className="shell footer-inner"><span className="brand-name">SUABHI<span> CAB</span></span><span>Private travel, thoughtfully arranged.</span></div></footer>
    </>
  );
}
