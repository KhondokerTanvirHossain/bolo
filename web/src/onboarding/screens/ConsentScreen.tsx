"use client";
import { useState } from "react";
import { Icon, Scene } from "@/scene/Scene";

type Props = {
  over13: boolean;
  busy: boolean;
  onToggleOver13: () => void;
  onChoose: (keep: boolean) => void;
};

/** US-1.1a: decide what Bolo keeps, before it listens. Nothing is pre-selected; both choices look equal. */
export function ConsentScreen({ over13, busy, onToggleOver13, onChoose }: Props) {
  const [under13, setUnder13] = useState(false);
  return (
    <section className="dk-screen on" data-screen="consent">
      <Scene />
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="consent-title">
        {under13 ? (
          <>
            <h1 className="bo-flow__title" id="consent-title">Bolo is for ages 13 and up</h1>
            <p className="bo-flow__body">Thanks for checking. Bolo can&apos;t be used under 13.</p>
            <p className="bo-flow__bn">বোলো ১৩ বছর বা তার বেশি বয়সীদের জন্য।</p>
          </>
        ) : (
          <>
            <div className="dk-steps"><span>Before Bolo listens</span><span>1 of 5</span></div>
            <div className="bo-hero-icon bo-hero-icon--coach" style={{ width: 64, height: 64, marginBottom: 18 }}>
              <Icon name="shield_person" />
            </div>
            <h1 className="bo-flow__title" id="consent-title">Bolo listens only while you practise.</h1>
            <p className="bo-flow__body">Keep your recordings so you can hear your progress later?</p>
            <p className="bo-flow__bn">রেকর্ডিং রেখে দেবেন, যাতে পরে নিজের উন্নতি শুনতে পারেন?</p>
            <label className={`bo-check${over13 ? " bo-check--on" : ""}`} style={{ marginTop: 18 }}>
              <input type="checkbox" checked={over13} onChange={onToggleOver13} />
              <span className="bo-check__box"><Icon name="check" size="sm" /></span>I&apos;m 13 or older
            </label>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 12 }}>
              <button className="bo-btn bo-btn--secondary" disabled={!over13 || busy} onClick={() => onChoose(true)}>
                Yes, keep them
              </button>
              <button className="bo-btn bo-btn--secondary" disabled={!over13 || busy} onClick={() => onChoose(false)}>
                No, just feedback
              </button>
            </div>
            <p className="bo-footnote" style={{ marginTop: 14 }}>You can change this any time in Me → Privacy.</p>
            <button className="bl-quiet-link" onClick={() => setUnder13(true)}>I&apos;m under 13</button>
          </>
        )}
      </div>
    </section>
  );
}
