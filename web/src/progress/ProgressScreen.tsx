"use client";
import { useEffect, useState } from "react";
import { fetchProgress, type Progress } from "./progress";
import { PatternList } from "./PatternList";
import { SessionRow } from "./SessionRow";
import { SkillTrends } from "./SkillTrends";

/**
 * ScreenProgress (PRG-1/3/8): effort first (it always grows), then skills in words, my patterns, and the latest talks.
 * Never a score or a percentage.
 */
export function ProgressScreen({ onOpen, onSeeAll, onPractise, onTalk }: {
  onOpen: (talkId: string) => void; onSeeAll: () => void; onPractise: (pattern: string) => void; onTalk: () => void;
}) {
  const [p, setP] = useState<Progress | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => { void fetchProgress().then((r) => { if (r) setP(r); else setFailed(true); }); }, []);
  if (failed) return <p className="bo-footnote" role="alert">Progress can&apos;t load right now. Check your connection.</p>;
  if (!p) return null;
  const { talks, minutes } = p.effort;
  if (talks === 0) {
    return (
      <>
        <h1 className="dk-h1">Progress</h1>
        <div className="bo-state">
          <p className="bo-state__title">Your progress starts with your first talk</p>
          <button className="bo-btn bo-btn--primary" onClick={onTalk}>Talk</button>
        </div>
      </>
    );
  }
  return (
    <>
      <h1 className="dk-h1">Progress</h1>
      <p className="dk-sub">{talks} {talks === 1 ? "talk" : "talks"} · {minutes} {minutes === 1 ? "minute" : "minutes"} of speaking</p>
      <h2 className="dk-h2" style={{ marginTop: 28 }}>Skills <span className="bo-row__sub">Last 30 days</span></h2>
      <SkillTrends trends={p.trends} />
      <h2 className="dk-h2" style={{ marginTop: 28 }}>My patterns</h2>
      <PatternList patterns={p.patterns} onPractise={onPractise} />
      <h2 className="dk-h2" style={{ marginTop: 28 }}>History <button className="dk-link" onClick={onSeeAll}
        style={{ border: 0, background: "transparent", padding: 0 }}>See all</button></h2>
      <div className="bo-group">{p.latest.map((r) => <SessionRow key={r.talkId} row={r} onOpen={onOpen} />)}</div>
    </>
  );
}
