"use client";
import { useEffect, useState } from "react";
import { fetchHistory, monthOf, type Row } from "./progress";
import { SessionRow } from "./SessionRow";

/** "This week", "Last week", then month names (ScreenHistory). Weeks start on Monday, Bangladesh time. */
export function weekLabel(iso: string, now: Date = new Date()): string {
  const DAY = 86_400_000;
  const dhaka = (d: Date) => new Date(d.getTime() + 6 * 3_600_000);
  const monday = (d: Date) => {
    const x = dhaka(d);
    const dow = (x.getUTCDay() + 6) % 7;
    return Math.floor((Date.UTC(x.getUTCFullYear(), x.getUTCMonth(), x.getUTCDate()) - dow * DAY) / DAY);
  };
  const diff = monday(now) - monday(new Date(iso));
  if (diff <= 0) return "This week";
  if (diff <= 7) return "Last week";
  return monthOf(iso, "long");
}

/** ScreenHistory (PRG-8, US-6.7): every past talk, newest first, grouped by week. Never limited by plan. */
export function HistoryScreen({ onOpen, onTalk }: { onOpen: (talkId: string) => void; onTalk: () => void }) {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => { void fetchHistory().then((r) => { if (r) setRows(r); else setFailed(true); }); }, []);
  if (failed) return <p className="bo-footnote" role="alert">History can&apos;t load right now. Check your connection.</p>;
  if (!rows) return null;
  if (rows.length === 0) {
    return (
      <div className="bo-state">
        <p className="bo-state__title">Your talks will appear here</p>
        <button className="bo-btn bo-btn--primary" onClick={onTalk}>Talk</button>
      </div>
    );
  }
  const groups: [string, Row[]][] = [];
  for (const r of rows) {
    const label = weekLabel(r.startedAt);
    const last = groups.at(-1);
    if (last && last[0] === label) last[1].push(r);
    else groups.push([label, [r]]);
  }
  return (
    <>
      <h1 className="dk-h1">History</h1>
      {groups.map(([label, list]) => (
        <section key={label}>
          <h2 className="bo-week-label">{label}</h2>
          <div className="bo-group">{list.map((r) => <SessionRow key={r.talkId} row={r} onOpen={onOpen} />)}</div>
        </section>
      ))}
    </>
  );
}
