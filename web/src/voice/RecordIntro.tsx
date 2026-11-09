"use client";
import { useCallback, useEffect, useMemo } from "react";
import { fetchMe } from "@/account/session";
import { Day1Screen, type IntroCopy } from "@/onboarding/screens/Day1Screen";
import { introKey, recordIntro } from "./voice";

/**
 * The intro again on Day {@code day} (PRG-2, US-6.1): the Day 1 recorder with its own words. The clip stays in this
 * browser; it goes to the server only for an account that keeps recordings, read fresh at save time. "Saved" shows only
 * once the server has it; Esc goes back.
 */
export function RecordIntro({ day, learnerId, deviceId, onDone, onBack }: { day: number; learnerId: string; deviceId: string | null;
  onDone: () => void; onBack: () => void }) {
  const copy: IntroCopy = useMemo(() => ({
    step: `Day ${day}`, stepRight: "Your intro again", title: "Introduce yourself again, like on Day 1",
    body: "30 seconds. Then hear Day 1 and today side by side.", short: "A few more seconds helps you hear the difference.",
    saved: "Saved. Here's how far you've come.",
  }), [day]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") { e.preventDefault(); onBack(); } };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onBack]);

  const saveNow = useCallback(async (clip: Blob) => {
    const me = await fetchMe();
    const keep = me?.kind === "account" && me.consent?.keepRecordings === true;
    const sent = await recordIntro(day, keep ? clip : null);
    // The choice changed on the server since: keep the clip here and note the date only.
    if (sent === "not-kept") return (await recordIntro(day, null)) === "ok";
    return sent === "ok";
  }, [day]);

  return <Day1Screen deviceId={deviceId} onSaved={onDone} clipKey={introKey(learnerId, day)} copy={copy} saveNow={saveNow} />;
}
