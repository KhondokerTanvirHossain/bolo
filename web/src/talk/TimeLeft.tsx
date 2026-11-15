"use client";
import { useEffect, useState } from "react";
import { timeLeftLabel, type Clock } from "./talkState";
import { talkLabel } from "@/scenario/scenario";

/**
 * The time left in numbers (TLK-16 changed 2026-11-12): "3:42 left", then "1 minute left", then "Wrapping up". It uses
 * the same colours throughout and makes no sound. Only those two changes are announced. Before the server's first time
 * arrives it shows the talk's length.
 */
/** SCN-1 (DS v44 ScreenStandupTalk): a standup's pill also names it ("Standup · Realistic"). */
export function TimeLeft({ clock, minutes, scenario, difficulty }: { clock: Clock | null; minutes: number; scenario?: string; difficulty?: string | null }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!clock || clock.paused) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [clock]);
  const label = timeLeftLabel(clock, clock?.paused ? clock.at : Math.max(now, clock?.at ?? 0));
  const what = talkLabel({ scenario, difficulty });
  if (!label) return <span className="bo-timeleft">{what ? `${what} · ${minutes} min` : `Free talk · ${minutes} min`}</span>;
  const announce = label === "1 minute left" || label === "Wrapping up";
  return <span className="bo-timeleft" role="timer" aria-live={announce ? "polite" : "off"}>{what && <span className="bo-timeleft__what">{what}</span>}{label}</span>;
}
