"use client";
import { useEffect, useState } from "react";
import { loadClip } from "@/audio/clipStore";
import { Clip } from "@/coach/Clip";
import type { Progress } from "@/progress/progress";
import { daysUntil, fetchVoice, summaryLine, type Voice } from "./voice";

/** A playable source for a clip: this browser first, then the server copy (kept recordings only). */
function useClipUrl(localKey: string, serverUrl: string | null): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let made: string | null = null;
    let cancelled = false;
    void (async () => {
      const local = await loadClip(localKey);
      if (local) { made = URL.createObjectURL(local); if (!cancelled) setUrl(made); return; }
      if (!serverUrl) return;
      const res = await fetch(serverUrl, { credentials: "same-origin" }).catch(() => null);
      if (!res?.ok) return;
      made = URL.createObjectURL(await res.blob());
      if (!cancelled) setUrl(made);
    })();
    return () => { cancelled = true; if (made) URL.revokeObjectURL(made); };
  }, [localKey, serverUrl]);
  return url;
}

/**
 * Your voice, then and now (ScreenProgress item 2, PRG-2): Day 1 and the latest intro side by side with one line on
 * what changed; between unlocks, Day 1 and when the next one opens.
 */
export function VoiceCompare({ progress, onRecord }: { progress: Progress | null; onRecord: (day: number) => void }) {
  const [voice, setVoice] = useState<Voice | null>(null);
  useEffect(() => { void fetchVoice().then(setVoice); }, []);
  const latest = voice?.recordings.length ? voice.recordings[voice.recordings.length - 1] : null;
  const day1 = useClipUrl("day1", "/api/me/day1");
  const now = useClipUrl(latest ? `intro-${latest.day}` : "none", latest?.onServer ? `/api/me/intro/${latest.day}` : null);
  if (!voice) return null;
  const left = daysUntil(voice.nextOn);
  return (
    <section aria-labelledby="voice-title">
      <h2 className="dk-h2" id="voice-title" style={{ marginTop: 28 }}>Your voice, then and now</h2>
      <div className="bo-compare">
        {day1 && (
          <div className="bo-compare__side">
            <div className="bo-compare__day"><small>Your first intro</small>Day 1</div>
            <Clip src={day1} label="Play your Day 1 intro" />
          </div>
        )}
        {latest && (
          <div className="bo-compare__side bo-compare__side--now">
            <div className="bo-compare__day"><small>Today</small>Day {latest.day}</div>
            <Clip src={now} label={`Play your Day ${latest.day} intro`} />
          </div>
        )}
      </div>
      {latest && (
        <p className="bo-row__sub" style={{ margin: "10px 0 0" }} data-voice-summary>
          {day1 ? summaryLine(progress) : "Your Day 1 clip isn't here, so this recording is your new starting point."}
        </p>
      )}
      {voice.due !== null ? (
        <div className="bo-card bo-lockcard" style={{ marginTop: 12 }}>
          <div style={{ flex: 1 }}><b>Day {voice.due} is here</b>
            <div className="bo-row__sub">Record your intro again and hear how far you&apos;ve come.</div></div>
          <button className="bo-btn bo-btn--primary" onClick={() => onRecord(voice.due!)}>Record your intro</button>
        </div>
      ) : (
        <p className="bo-row__sub" style={{ margin: "10px 0 0" }} data-voice-next>
          {left === 0 ? `Day ${voice.nextDay} is here` : `Day ${voice.nextDay} unlocks in ${left} ${left === 1 ? "day" : "days"}`}
        </p>
      )}
    </section>
  );
}
