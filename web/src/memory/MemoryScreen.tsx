"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { fetchMemory, forgetAll, KIND_LABEL, removeLine, saidLabel, setRemembering, type MemoryView } from "./memory";

const EMPTY = { followUps: [], about: [], interests: [] };
const FAILED = "That didn't save. Try again.";

/**
 * Me → What Bolo remembers (P3b; design system v35 ScreenMemory, ScreenMemoryEmpty, DesktopMemory; ACC-9 2026-11-13):
 * Bolo's short notes, To follow up first. Remove any line (at once, no confirm), switch remembering off, or forget all.
 */
export function MemoryScreen({ onBack }: { onBack: () => void }) {
  const [v, setV] = useState<MemoryView | null | undefined>(undefined);
  const [problem, setProblem] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);
  useEffect(() => { void fetchMemory().then(setV); }, []);

  const back = <button className="bo-back" onClick={onBack}><Icon name="chevron_left" />Me</button>;
  if (v === undefined) return back;
  if (v === null) {
    return (
      <section>
        {back}
        <p className="bo-footnote" role="alert">Bolo&apos;s notes didn&apos;t load. Check your connection, then try again.</p>
      </section>
    );
  }

  const remove = async (id: string) => {
    const before = v;
    setProblem(null);
    setV({ ...v, followUps: v.followUps.filter((t) => t.id !== id), about: v.about.filter((l) => l.id !== id),
      interests: v.interests.filter((l) => l.id !== id) });
    if (!(await removeLine(id))) {
      setV(before);
      setProblem(FAILED);
    }
  };
  const flip = async () => {
    const before = v;
    const on = !v.enabled;
    setProblem(null);
    setV(on ? { ...v, enabled: true } : { enabled: false, ...EMPTY });
    if (!(await setRemembering(on))) {
      setV(before);
      setProblem(FAILED);
    }
  };
  const forget = async () => {
    const before = v;
    setAsking(false);
    setProblem(null);
    setV({ ...v, ...EMPTY });
    if (!(await forgetAll())) {
      setV(before);
      setProblem(FAILED);
    }
  };

  const empty = v.followUps.length === 0 && v.about.length === 0 && v.interests.length === 0;
  const removeButton = (id: string, text: string) => (
    <button className="bo-row__remove" aria-label={`Remove: ${text}`} onClick={() => void remove(id)}><Icon name="close" /></button>
  );

  return (
    <section className="bo-memory">
      {back}
      <h1 className="dk-h1">What Bolo remembers</h1>
      <p className="bo-memnote">Bolo keeps short notes from your talks so it can follow up, like a friend would. Only you can see them. Remove any line, or turn this off.</p>
      {problem && <p className="bo-footnote" role="alert" style={{ textAlign: "left", marginBottom: 12 }}>{problem}</p>}

      {empty ? (
        <div className="bo-card bo-memory__empty">
          <Icon name="auto_stories" />
          {v.enabled ? (
            <>
              <p className="headline">Nothing yet</p>
              <p className="bo-row__sub">After your next talk, the things Bolo will ask you about show up here.</p>
            </>
          ) : <p className="bo-row__sub">Bolo isn&apos;t keeping notes. Each talk starts fresh.</p>}
        </div>
      ) : (
        <>
          {v.followUps.length > 0 && (
            <>
              <h2 className="bo-group__title">To follow up</h2>
              <div className="bo-group">
                {v.followUps.map((t) => (
                  <div className="bo-row" key={t.id}>
                    <div className="bo-row__body">
                      <span className="bo-row__kind">{KIND_LABEL[t.kind]}</span>
                      <div className="bo-row__title">{t.text}</div>
                      <div className="bo-row__sub">{saidLabel(t.said)}</div>
                    </div>
                    {removeButton(t.id, t.text)}
                  </div>
                ))}
              </div>
            </>
          )}
          <div className="bo-memory__pair">
            {v.about.length > 0 && (
              <div>
                <h2 className="bo-group__title">About you</h2>
                <div className="bo-group">
                  {v.about.map((l) => (
                    <div className="bo-row" key={l.id}><div className="bo-row__body"><div className="bo-row__title">{l.text}</div></div>{removeButton(l.id, l.text)}</div>
                  ))}
                </div>
              </div>
            )}
            {v.interests.length > 0 && (
              <div>
                <h2 className="bo-group__title">Your interests</h2>
                <div className="bo-group">
                  {v.interests.map((l) => (
                    <div className="bo-row" key={l.id}><div className="bo-row__body"><div className="bo-row__title">{l.text}</div></div>{removeButton(l.id, l.text)}</div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </>
      )}

      <div className="bo-group" style={{ marginTop: 20 }}>
        <div className="bo-row">
          <div className="bo-row__body">
            <div className="bo-row__title" id="remember-title">Let Bolo remember our talks</div>
            <div className="bo-row__sub">Off: Bolo starts fresh each time</div>
          </div>
          <button className="bo-toggle" role="switch" aria-checked={v.enabled} aria-labelledby="remember-title" onClick={() => void flip()} />
        </div>
      </div>

      {!empty && !asking && (
        <button className="bo-btn bo-btn--quiet-neutral" style={{ marginTop: 12, paddingLeft: 0 }} onClick={() => setAsking(true)}>Forget everything Bolo knows</button>
      )}
      {asking && (
        <div className="bo-card" style={{ marginTop: 12 }} role="group" aria-label="Forget everything Bolo knows">
          <p className="bo-row__sub" style={{ fontSize: 15, lineHeight: "22px", margin: "0 0 12px" }}>Bolo will forget everything it knows about you. Talks still work as before.</p>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button className="bo-btn bo-btn--secondary" onClick={() => void forget()}>Forget everything</button>
            <button className="bo-btn bo-btn--quiet-neutral" onClick={() => setAsking(false)}>Cancel</button>
          </div>
        </div>
      )}
    </section>
  );
}
