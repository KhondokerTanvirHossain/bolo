"use client";
import { useEffect, useState } from "react";
import { Icon, Scene } from "@/scene/Scene";
import { fetchPractice, type Pattern } from "./attempts";
import { ReadAloud } from "./ReadAloud";

/** Practise one sound at a time: pick a sound, read its sentences aloud, see what to work on (ASM-2, US-4.3). */
/** {@code initialPattern}: open straight on this sound (Home's "Practise the v sound"). */
export function PracticeScreen({ deviceId, onBack, initialPattern }: { deviceId: string | null; onBack: () => void; initialPattern?: string }) {
  const [patterns, setPatterns] = useState<Pattern[] | null>(null);
  const [chosen, setChosen] = useState<Pattern | null>(null);
  const [n, setN] = useState(0);

  useEffect(() => {
    void fetchPractice().then((p) => {
      setPatterns(p);
      const start = initialPattern ? p.find((x) => x.key === initialPattern) : undefined;
      if (start) setChosen(start);
    });
  }, [initialPattern]);

  return (
    <section className="dk-screen on" data-screen="drill">
      <Scene />
      <div className="dk-dim" />
      <div className={`dk-card${chosen ? " dk-card--wide" : ""}`} role="dialog" aria-modal="true" aria-label="Practise sounds">
        {!chosen ? (
          <>
            <h1 className="bo-flow__title">Practise sounds</h1>
            <p className="bo-flow__body">Pick a sound. Read each sentence aloud and see how it went.</p>
            <div style={{ display: "grid", gap: 10, marginTop: 16 }}>
              {(patterns ?? []).map((p) => (
                <button key={p.key} className="bo-btn bo-btn--secondary bo-btn--block" style={{ justifyContent: "space-between" }}
                  onClick={() => { setChosen(p); setN(0); }}>
                  <span>{p.title}</span><span className="bo-footnote">{p.example}</span>
                </button>
              ))}
              {patterns?.length === 0 && <p className="bo-footnote" role="alert">Practice isn&apos;t available right now. Try again soon.</p>}
            </div>
            <button className="bo-btn bo-btn--quiet-neutral bo-btn--block" style={{ marginTop: 16 }} onClick={onBack}>Back</button>
          </>
        ) : (
          <>
            <div className="dk-steps"><span>{chosen.title} · {chosen.example}</span><span>{n + 1} of {chosen.sentences.length}</span></div>
            <div className="bo-tip" style={{ margin: "12px 0 20px" }}><Icon name="sentiment_satisfied" /><p className="bo-tip__text">{chosen.tip}</p></div>
            <ReadAloud reference={chosen.sentences[n]} deviceId={deviceId} />
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 24 }}>
              <button className="bo-btn bo-btn--secondary" onClick={() => setChosen(null)}>Other sounds</button>
              <button className="bo-btn bo-btn--primary" onClick={() => setN((x) => (x + 1) % chosen.sentences.length)}>Next sentence</button>
            </div>
          </>
        )}
      </div>
    </section>
  );
}
