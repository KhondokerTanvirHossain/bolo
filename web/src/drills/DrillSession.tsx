"use client";
import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { fetchDrills, finishDrill, queueRest, skipDrill, summaryLines, withQueue, type DrillResult, type Planned } from "./drills";
import { PairsDrill } from "./PairsDrill";
import { SayBetterDrill } from "./SayBetterDrill";
import { ShadowDrill } from "./ShadowDrill";

/**
 * Practise (3 min) (DRL-1…7, DesktopDrill): up to 3 drills from this talk's fixes (queued ones first), with Close (Esc),
 * three dots and Skip above one card; then what moved, and one Done. Drills never affect the loop or the streak.
 */
export function DrillSession({ talkId, deviceId, onDone, onPractiseSounds }: { talkId: string; deviceId: string | null;
  onDone: () => void; onPractiseSounds: () => void }) {
  const [plan, setPlan] = useState<Planned[] | null>(null);
  const [at, setAt] = useState(0);
  const [results, setResults] = useState<DrillResult[]>([]);

  useEffect(() => { void fetchDrills(talkId).then((d) => setPlan(withQueue(d ?? [], talkId))); }, [talkId]);

  const close = useCallback(() => {
    // Closing midway (US-5.5): what's done is kept; the rest come back next time.
    if (plan) queueRest(plan.slice(at));
    onDone();
  }, [plan, at, onDone]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") { e.preventDefault(); close(); } };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  if (!plan) return null;
  const current = plan[at];
  const finish = (r: DrillResult) => {
    setResults((x) => [...x, r]);
    setAt((n) => n + 1);
  };

  return (
    <section className="dk-screen on dk-drillpage" data-screen="drill">
      <div className="dk-drill__head">
        <button className="bo-btn bo-btn--quiet-neutral" aria-label="Close" onClick={close}><Icon name="close" /></button>
        <span className="dk-drill__dots" aria-label={`Drill ${Math.min(at + 1, plan.length)} of ${plan.length}`}>
          {plan.map((p, i) => <i key={p.drill.key} className={i < at ? "done" : i === at ? "on" : ""} />)}
        </span>
        {current ? (
          <button className="bo-btn bo-btn--quiet-neutral" onClick={() => {
            skipDrill(current.drill, current.talkId);
            finish({ title: current.drill.title, skipped: true });
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
            <ul className="dk-drill__summary">{summaryLines(results).map((l) => <li key={l}>{l}</li>)}</ul>
            <button className="bo-btn bo-btn--primary" autoFocus onClick={onDone}>Done</button>
          </div>
        ) : (
          <div key={`${at}-${current.drill.key}`}>
            {current.drill.kind === "pairs" && <PairsDrill drill={current.drill} talkId={current.talkId} deviceId={deviceId}
              onFinish={(f, b) => { finishDrill(current.drill); finish({ title: current.drill.title, first: f, best: b }); }} />}
            {current.drill.kind === "shadow" && <ShadowDrill drill={current.drill} talkId={current.talkId} deviceId={deviceId}
              onFinish={(f, b) => { finishDrill(current.drill); finish({ title: current.drill.title, first: f, best: b }); }} />}
            {current.drill.kind === "sayBetter" && <SayBetterDrill drill={current.drill} deviceId={deviceId}
              onFinish={(f, b) => { finishDrill(current.drill); finish({ title: current.drill.title, first: f, best: b }); }} />}
          </div>
        )}
      </div>
    </section>
  );
}
