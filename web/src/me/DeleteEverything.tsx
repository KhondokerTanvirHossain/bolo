"use client";
import { useDialog } from "@/ui/useDialog";
import { useState, useRef } from "react";
import { api } from "@/api/browser";

/** US-8.6: Delete everything, confirmed by typing DELETE. The app's only critical button (design: ScreenPrivacy). */
export function DeleteEverything({ onCancel, onDeleted }: { onCancel: () => void; onDeleted: () => void }) {
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const ready = typed === "DELETE";
  const remove = async () => {
    setBusy(true);
    setFailed(false);
    const r = await api.post<void>("/api/me/delete", { confirm: typed });
    setBusy(false);
    if (r.kind === "ok") onDeleted();
    else setFailed(true);
  };
  const box = useRef<HTMLDivElement>(null);
  useDialog(box, () => { if (!busy) onCancel(); });
  return (
    <>
      <div className="dk-dim" />
      <div className="dk-card" ref={box} role="dialog" aria-modal="true" aria-labelledby="delete-title">
        <h1 className="bo-flow__title" id="delete-title">Delete everything?</h1>
        <p className="bo-flow__body">Your account, your talks, your feedback and your recordings are deleted for good. This can&apos;t be undone.</p>
        <label className="bo-fix__label" htmlFor="delete-confirm" style={{ display: "block", marginTop: 16 }}>Type DELETE to confirm</label>
        <input id="delete-confirm" value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" autoFocus
          style={{ width: "100%", height: 48, marginTop: 6, padding: "0 14px", borderRadius: 12, border: "1.5px solid var(--line-strong)",
            font: "600 17px/22px var(--font-sans)", background: "var(--surface)", color: "var(--ink)" }} />
        {failed && <p className="bo-footnote" role="alert" style={{ marginTop: 10 }}>That didn&apos;t work. Check your connection and try again.</p>}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 18 }}>
          <button className="bo-btn bo-btn--secondary" onClick={onCancel} disabled={busy}>Keep my data</button>
          <button className="bo-btn bo-btn--critical" onClick={() => void remove()} disabled={!ready || busy}>Delete everything</button>
        </div>
      </div>
    </>
  );
}
