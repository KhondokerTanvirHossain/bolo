"use client";
import { useEffect, useState } from "react";
import { timeLeftLabel, type Clock } from "./talkState";

/**
 * The time left in numbers (TLK-16 changed 2026-11-12): "3:42 left", then "1 minute left", then "Wrapping up". It uses
 * the same colours throughout and makes no sound. Only those two changes are announced. Before the server's first time
 * arrives it shows the talk's length.
 */
export function TimeLeft({ clock, minutes }: { clock: Clock | null; minutes: number }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!clock || clock.paused) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [clock]);
  const label = timeLeftLabel(clock, clock?.paused ? clock.at : Math.max(now, clock?.at ?? 0));
  if (!label) return <span className="bo-timeleft">Free talk · {minutes} min</span>;
  const announce = label === "1 minute left" || label === "Wrapping up";
  return <span className="bo-timeleft" role="timer" aria-live={announce ? "polite" : "off"}>{label}</span>;
}
