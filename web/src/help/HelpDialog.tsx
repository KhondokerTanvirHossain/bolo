"use client";
import { useState } from "react";
import { DialogCard } from "@/ui/DialogCard";
import { reportProblem, type HelpContext } from "./help";

/** Report a problem (P2b): one note; the screen and last talk go with it; no audio, no transcript. */
export function HelpDialog({ ctx, onClose }: { ctx: HelpContext; onClose: () => void }) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const send = async () => {
    setBusy(true);
    const r = await reportProblem(text.trim(), ctx);
    setBusy(false);
    if (r === "ok") { setSent(true); setTimeout(onClose, 1500); }
    else setNote(r === "too-many" ? "That's a lot of reports. Please try again in a while." : "That didn't send. Check your connection, then try again.");
  };
  return (
    <>
      <div className="dk-dim" />
      <DialogCard labelledBy="help-title" onEscape={onClose}>
        <h1 className="bo-flow__title" id="help-title">Report a problem</h1>
        {sent ? <p className="bo-flow__body" role="status">Thanks. We read every one.</p> : (
          <>
            <label className="bo-flow__body" htmlFor="help-text">What went wrong?</label>
            <textarea id="help-text" className="ad-input dk-help__text" maxLength={1000} value={text} onChange={(e) => setText(e.target.value)} autoFocus />
            <p className="bo-footnote">We&apos;ll include: the screen you&apos;re on and your last talk. Or write to k.tanvir.hossain@gmail.com.</p>
            {note && <p className="bo-footnote" role="alert">{note}</p>}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 16 }}>
              <button className="bo-btn bo-btn--secondary" onClick={onClose}>Cancel</button>
              <button className="bo-btn bo-btn--primary" disabled={busy || text.trim().length === 0} onClick={() => void send()}>Send</button>
            </div>
          </>
        )}
      </DialogCard>
    </>
  );
}
