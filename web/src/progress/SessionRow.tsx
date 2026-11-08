"use client";
import { dayOf, monthOf, type Row } from "./progress";

/** SessionRow (PRG-8): the whole row is the target; a diary-like date; fix tags in the Coach's violet. */
export function SessionRow({ row, onOpen }: { row: Row; onOpen: (talkId: string) => void }) {
  const n = row.fixes.length;
  const what = row.status === "too_short" ? "short talk" : row.status === "failed" ? "no feedback"
    : `${n} ${n === 1 ? "thing" : "things"} to try`;
  const label = `${dayOf(row.startedAt)} ${monthOf(row.startedAt, "long")}, ${row.topic}, free talk, ${row.minutes} minutes, ${what}`;
  return (
    <button className="bo-session" aria-label={label} onClick={() => onOpen(row.talkId)}
      style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }}>
      <div className="bo-session__date" aria-hidden="true">
        <div className="bo-session__day">{dayOf(row.startedAt)}</div>
        <div className="bo-session__mon">{monthOf(row.startedAt)}</div>
      </div>
      <div className="bo-session__body" aria-hidden="true">
        <div className="bo-session__topic">{row.topic}</div>
        <div className="bo-session__meta">Free talk · {row.minutes} min</div>
        <div className="bo-session__fixes">
          {row.status === "ready"
            ? row.fixes.map((f) => <span className="bo-fixtag" key={f}>{f}</span>)
            : <span className="bo-tag bo-tag--steady">{row.status === "too_short" ? "Short talk" : "No feedback"}</span>}
        </div>
      </div>
    </button>
  );
}
