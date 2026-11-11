"use client";
import Link from "next/link";
import { useState } from "react";
import { DialogCard } from "@/ui/DialogCard";
import { copyText } from "@/referral/referral";
import { cleanTrxId, payerOk, payProblem, submitPayment, trxOk } from "./payments";
import { planTitle, type PaidPlan } from "./pricing";

/**
 * Pay with bKash (P2e, Option A; ScreenPlanPay): Send Money to the founder's number, then the TrxID and the number paid
 * from. Checked by hand; the plan starts when it's confirmed. The renewal and refund lines come before paying.
 */
export function BkashSheet({ plan, number, onClose, onSent }: { plan: PaidPlan; number: string; onClose: () => void; onSent: () => void }) {
  const [trx, setTrx] = useState("");
  const [payer, setPayer] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const name = planTitle(plan.plan);

  const send = async () => {
    if (!trxOk(cleanTrxId(trx)) || !payerOk(payer)) { setNote(payProblem.bad); return; }
    setBusy(true);
    const r = await submitPayment(plan.plan, trx, payer);
    setBusy(false);
    if (r === "ok") { setSent(true); setNote(null); onSent(); }
    else setNote(payProblem[r]);
  };

  return (
    <>
      <div className="dk-dim" />
      <DialogCard labelledBy="bkash-title" onEscape={onClose}>
        <h1 className="bo-flow__title" id="bkash-title">Pay with bKash</h1>
        {sent ? (
          <>
            <p className="bo-flow__body" role="status">Thanks. We&apos;re checking your payment, usually within a few hours. Your plan starts when it&apos;s confirmed.</p>
            <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 16 }} onClick={onClose} autoFocus>OK</button>
          </>
        ) : (
          <>
            <ol className="bo-bkash__steps">
              <li>Open bKash and choose <b>Send Money</b>.</li>
              <li>Send <b>৳{plan.priceBdt}</b> to <b>{number}</b>
                <button type="button" className="dk-link bo-bkash__copy" onClick={async () => setNote((await copyText(number)) ? "Number copied" : null)}>Copy number</button></li>
              <li>Type the Transaction ID (TrxID) from bKash&apos;s message, and the bKash number you paid from.</li>
            </ol>
            <label className="ad-label bo-bkash__field">TrxID
              <input className="ad-input" value={trx} onChange={(e) => setTrx(e.target.value)} autoComplete="off" autoCapitalize="characters" maxLength={20} autoFocus />
            </label>
            <label className="ad-label bo-bkash__field">Your bKash number
              <input className="ad-input" value={payer} onChange={(e) => setPayer(e.target.value)} inputMode="numeric" autoComplete="tel" maxLength={14} placeholder="01XXXXXXXXX" />
            </label>
            <p className="bo-footnote" style={{ textAlign: "left", marginTop: 12 }}>
              {name} for 30 days from approval. It doesn&apos;t renew by itself. Refunds: see the <Link className="dk-link" href="/refund">refund policy</Link>.
            </p>
            {note && <p className="bo-footnote" role="status" style={{ marginTop: 8 }}>{note}</p>}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 16 }}>
              <button className="bo-btn bo-btn--secondary" onClick={onClose}>Cancel</button>
              <button className="bo-btn bo-btn--primary" disabled={busy} onClick={() => void send()}>Send for checking</button>
            </div>
          </>
        )}
      </DialogCard>
    </>
  );
}
