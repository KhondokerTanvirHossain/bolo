"use client";
import { Icon } from "@/scene/Scene";
import type { Week } from "./home";

const NAMES = ["Sa", "Su", "Mo", "Tu", "We", "Th", "Fr"];
const LONG = ["Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
const SAID: Record<string, string> = { done: "talked or practised", rest: "rest day", today: "today", missed: "no talk", future: "still to come" };

/** This week (PRG-4, US-6.4): a gentle run with 1 rest day a week. Never a broken flame, never guilt. */
export function WeekCard({ week }: { week: Week }) {
  const title = week.run > 0 ? `${week.run}-day run` : week.welcomeBack ? "Welcome back. Let's start a new run." : "Your run starts with your first talk";
  return (
    <div className="bo-card" data-week>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, marginBottom: 12, flexWrap: "wrap" }}>
        <b>{title}</b>
        {week.restUsed && <span className="subhead" style={{ color: "var(--ink-secondary)" }}>1 rest day used</span>}
      </div>
      <ol className="bo-week" aria-label="This week" style={{ listStyle: "none", margin: 0, padding: 0 }}>
        {week.days.map((d, i) => (
          <li key={d.date} className={`bo-day bo-day--${d.state}`}>
            <span className="bo-sr">{`${LONG[i]}: ${SAID[d.state] ?? d.state}`}</span>
            <span className="bo-day__dot">
              {d.state === "done" && <Icon name="wb_sunny" fill />}
              {d.state === "rest" && <Icon name="bedtime" />}
              {d.state === "today" && <Icon name="wb_sunny" />}
            </span>
            <span aria-hidden="true">{NAMES[i]}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}
