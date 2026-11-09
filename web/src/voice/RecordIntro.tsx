"use client";
import { useCallback, useEffect, useState } from "react";
import { loadClip } from "@/audio/clipStore";
import { Day1Screen, type IntroCopy } from "@/onboarding/screens/Day1Screen";
import { recordIntro } from "./voice";

/** The intro again on Day {@code day} (PRG-2, US-6.1): the Day 1 recorder with its own words; Esc goes back. */
export function RecordIntro({ day, keep, deviceId, onDone, onBack }: { day: number; keep: boolean; deviceId: string | null;
  onDone: () => void; onBack: () => void }) {
  const [problem, setProblem] = useState(false);
  const copy: IntroCopy = {
    step: `Day ${day}`, stepRight: "Your intro again", title: "Introduce yourself again, like on Day 1",
    body: "30 seconds. Then hear Day 1 and today side by side.", short: "A few more seconds helps you hear the difference.",
    saved: "Saved. Here's how far you've come.",
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") { e.preventDefault(); onBack(); } };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onBack]);

  // The clip stays in this browser; it goes to the server only when the learner keeps recordings.
  const saved = useCallback(async () => {
    const clip = await loadClip(`intro-${day}`);
    const ok = await recordIntro(day, keep ? clip : null);
    if (ok) onDone(); else setProblem(true);
  }, [day, keep, onDone]);

  return (
    <>
      <Day1Screen deviceId={deviceId} onSaved={() => void saved()} clipKey={`intro-${day}`} copy={copy} />
      {problem && <p className="dk-toast show" role="alert">That didn&apos;t save. Check your connection, then try again.</p>}
    </>
  );
}
