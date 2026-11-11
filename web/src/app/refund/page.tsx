import type { Metadata } from "next";

export const metadata: Metadata = { title: "Bolo" };
const section = { margin: "28px 0 0" } as const;

/** Refund policy (P2b): simple and fair, for monthly plans paid by bKash. */
export default function RefundPage() {
  return (
    <div style={{ height: "100%", overflowY: "auto" }}>
      <main style={{ maxWidth: 680, margin: "0 auto", padding: "40px 16px 64px", color: "var(--ink)", font: "500 17px/27px var(--font-sans)" }}>
        <h1 className="bo-flow__title">Refund policy</h1>
        <p>Updated 11 November 2026.</p>

        <h2 className="title-2" style={section}>Monthly, no automatic renewal</h2>
        <p>Paid plans are for one month at a time. While payments are made by bKash, nothing renews by itself: you pay again only if you want another month.</p>

        <h2 className="title-2" style={section}>Changed your mind</h2>
        <p>Within 3 days of paying, if you have used at most 1 talk of that month, you get a full refund. After that, the month you have started is not refunded.</p>

        <h2 className="title-2" style={section}>Something went wrong on our side</h2>
        <p>If Bolo didn&apos;t work for you because of a problem on our side, we will give you the talks back or refund you.</p>

        <h2 className="title-2" style={section}>How to ask</h2>
        <p>Write to <a href="mailto:k.tanvir.hossain@gmail.com">k.tanvir.hossain@gmail.com</a> or use Me → Help → Report a problem, with the bKash transaction ID. Refunds go back to the bKash number you paid from, within 7 days.</p>
      </main>
    </div>
  );
}
