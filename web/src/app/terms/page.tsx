import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = { title: "Bolo" };
const section = { margin: "28px 0 0" } as const;

/** Terms of Use (P2b), in plain words. */
export default function TermsPage() {
  return (
    <div style={{ height: "100%", overflowY: "auto" }}>
      <main style={{ maxWidth: 680, margin: "0 auto", padding: "40px 16px 64px", color: "var(--ink)", font: "500 17px/27px var(--font-sans)" }}>
        <h1 className="bo-flow__title">Terms of Use</h1>
        <p>These are the rules for using Bolo. Updated 11 November 2026. By signing in you agree to them.</p>

        <h2 className="title-2" style={section}>Who can use Bolo</h2>
        <p>You must be 13 or older. If you are under 18, use Bolo with a parent&apos;s or guardian&apos;s okay. One account per person.</p>

        <h2 className="title-2" style={section}>What Bolo is</h2>
        <p>Bolo is an English speaking coach: you talk with Bolo, then get feedback and practice. It is not therapy, and not medical, legal, immigration or exam advice. If you are in danger or crisis, contact local emergency services.</p>

        <h2 className="title-2" style={section}>Bolo can be wrong</h2>
        <p>Bolo listens and gives feedback with computer speech recognition and AI. It can mishear you or make mistakes about pronunciation or grammar. Use &quot;That&apos;s not what I said&quot; on a feedback card when it does.</p>

        <h2 className="title-2" style={section}>Fair use</h2>
        <ul>
          <li>Use Bolo to practise your English. Don&apos;t abuse, threaten or harass, and don&apos;t try to make Bolo produce harmful content.</li>
          <li>Don&apos;t make several accounts or try to get around the talk limits.</li>
          <li>Don&apos;t try to break, overload or copy the service.</li>
        </ul>
        <p>We may limit or close an account that breaks these rules.</p>

        <h2 className="title-2" style={section}>Free and paid plans</h2>
        <p>The free plan gives 7 talks in your first week, then 2 a month. Paid plans give more talks each month; the prices and talk counts are on the <Link href="/pricing">pricing page</Link>. We may change plans and prices with notice; a month you have paid for keeps what it promised. On busy days Bolo may rest for everyone until the next day.</p>

        <h2 className="title-2" style={section}>Your content and privacy</h2>
        <p>What Bolo keeps, why and for how long is on the <a href="/privacy">Privacy page</a>. You can delete a recording, or everything, at any time in Me → Privacy. Bolo keeps short notes from your talks so it can follow up; you can see, remove or switch them off in Me → What Bolo remembers.</p>

        <h2 className="title-2" style={section}>Payments and refunds</h2>
        <p>See the <a href="/refund">Refund policy</a>.</p>

        <h2 className="title-2" style={section}>Changes and contact</h2>
        <p>We may update these terms; the date above shows the latest. Questions: <a href="mailto:k.tanvir.hossain@gmail.com">k.tanvir.hossain@gmail.com</a>.</p>
      </main>
    </div>
  );
}
