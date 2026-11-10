"use client";
import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { fetchDrills, finishDrill, queueRest, skipDrill, summaryLines, withQueue, type DrillResult, type Planned } from "./drills";
import { PairsDrill } from "./PairsDrill";
import { SayBetterDrill } from "./SayBetterDrill";
import { ShadowDrill } from "./ShadowDrill";
import { stopPlaying } from "./play";

/**
 * Practise (3 min) (DRL-1…7, DesktopDrill): up to 3 drills from this talk's fixes (queued ones first), with Close (Esc),
 * three dots and Skip above one card; then what moved, and one Done. Drills never affect the loop or the streak.
 */
export function DrillSession({ talkId, learnerId, deviceId, onDone, onPractiseSounds }: { talkId: string; learnerId: string;
  deviceId: string | null; onDone: () => void; onPractiseSounds: () => void }) {
  const [plan, setPlan] = useState<Planned[] | null>(null);
  const [at, setAt] = useState(0);
  const [results, setResults] = useState<DrillResult[]>([]);

  useEffect(() => { void fetchDrills(talkId).then((d) => setPlan(withQueue(d ?? [], talkId, learnerId))); }, [talkId, learnerId]);
  useEffect(() => stopPlaying, []);

  const close = useCallback(() => {
    // Closing midway (US-5.5): what's done is kept; the rest come back next time.
    stopPlaying();
    if (plan) queueRest(plan.slice(at), learnerId);
    onDone();
  }, [plan, at, onDone, learnerId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") { e.preventDefault(); close(); } };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  if (!plan) return null;
  const current = plan[at];
  const finish = (r: DrillResult) => {
    stopPlaying();
    setResults((x) => [...x, r]);
    setAt((n) => n + 1);
  };
  /** Done with a drill: it leaves the queue, unless nothing passed yet (then it comes back next time). */
  const done = (p: Planned, first?: DrillResult["first"], best?: DrillResult["best"]) => {
    if (best === "ONCE_MORE") skipDrill(p.drill, p.talkId, learnerId); else finishDrill(p.drill, learnerId);
    finish({ title: p.drill.title, first, best });
  };

  return (
    <section className="dk-screen on dk-drillpage" data-screen="drill">
      <div className="dk-drill__head">
        <button className="bo-btn bo-btn--quiet-neutral" aria-label="Close" onClick={close}><Icon name="close" /></button>
        <span className="dk-drill__dots" role="img" aria-label={`Drill ${Math.min(at + 1, plan.length)} of ${plan.length}`}>
          {plan.map((p, i) => <i key={p.drill.key} className={i < at ? "done" : i === at ? "on" : ""} />)}
        </span>
        {current ? (
          <button className="bo-btn bo-btn--quiet-neutral" onClick={() => {
            const retired = skipDrill(current.drill, current.talkId, learnerId);
            finish({ title: current.drill.title, skipped: true, retired });
          }}>Skip</button>
        ) : <span />}
      </div>
      <div className="dk-stagecard">
        {plan.length === 0 ? (
          <div className="bo-state">
            <p className="bo-state__title">Nothing to drill from this talk</p>
            <p className="bo-row__sub">Its feedback had no sound or sentence to practise. You can still practise sounds.</p>
            <button className="bo-btn bo-btn--primary" onClick={onPractiseSounds}>Practise sounds</button>
          </div>
        ) : !current ? (
          <div className="bo-state" data-drill="done">
            <p className="bo-state__title">Nice work.</p>
            <ul className="dk-drill__summary">{summaryLines(results).map((l, i) => <li key={i}>{l}</li>)}</ul>
            <button className="bo-btn bo-btn--primary" autoFocus onClick={onDone}>Done</button>
          </div>
        ) : (
          <div key={`${at}-${current.drill.key}`}>
            {current.drill.kind === "pairs" && <PairsDrill drill={current.drill} talkId={current.talkId} deviceId={deviceId}
              onFinish={(f, b) => done(current, f, b)} />}
            {current.drill.kind === "shadow" && <ShadowDrill drill={current.drill} talkId={current.talkId} deviceId={deviceId}
              onFinish={(f, b) => done(current, f, b)} />}
            {current.drill.kind === "sayBetter" && <SayBetterDrill drill={current.drill} deviceId={deviceId}
              onFinish={(f, b) => done(current, f, b)} />}
          </div>
        )}
      </div>
    </section>
  );
}
