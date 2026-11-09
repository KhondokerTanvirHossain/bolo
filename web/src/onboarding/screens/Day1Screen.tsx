"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { saveClip } from "@/audio/clipStore";
import { openMic } from "@/audio/openMic";
import { startRecording, type Recording } from "@/audio/recorder";
import { Icon, Scene } from "@/scene/Scene";
import { afterRecording, canRecordAgain, DAY1, startClip } from "../day1";

type Phase = "ready" | "recording" | "short" | "saving" | "failed" | "saved";
const ARC = 502.7;

/** The words around the recorder: Day 1 by default, or the intro again on Day 7/14/21 (PRG-2). */
export type IntroCopy = { step: string; stepRight: string; title: string; body: string; short: string; saved: string };
export const DAY1_COPY: IntroCopy = {
  step: "Your Day 1 clip", stepRight: "Last step", title: "Introduce yourself, like at a new job",
  body: "30 seconds. We'll play it back to you on Day 7.", short: "A few more seconds helps you hear your progress on Day 7.",
  saved: "Saved. We'll play this back to you on Day 7.",
};

/** "Introduce yourself, like at a new job" (prototype day1 card, US-1.6). The clip stays on this device ({@code clipKey}). */
/**
 * {@code saveNow}: when set (the intro re-record), the clip is sent right away and "Saved" shows only once it is; a
 * failure offers Try again with the same clip.
 */
export function Day1Screen({ deviceId, onSaved, clipKey = "day1", copy = DAY1_COPY, saveNow }: { deviceId: string | null; onSaved: () => void;
  clipKey?: string; copy?: IntroCopy; saveNow?: (clip: Blob) => Promise<boolean> }) {
  const [phase, setPhase] = useState<Phase>("ready");
  const [left, setLeft] = useState<number>(DAY1.maxSeconds);
  const [recordings, setRecordings] = useState(0);
  const [prompted, setPrompted] = useState(false);
  const [micProblem, setMicProblem] = useState(false);
  const rec = useRef<{ recording: Recording; stream: MediaStream; started: number; timer: ReturnType<typeof setInterval> } | null>(null);
  const lastClip = useRef<Blob | null>(null);
  const starting = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  /** Stores the clip on this device, then (re-record) sends it; "Saved" only once that worked. */
  const store = useCallback(async (blob: Blob) => {
    await saveClip(clipKey, blob);
    if (!saveNow) { setPhase("saved"); return; }
    setPhase("saving");
    const ok = await saveNow(blob);
    if (mounted.current) setPhase(ok ? "saved" : "failed");
  }, [clipKey, saveNow]);

  const finish = useCallback(async () => {
    const r = rec.current;
    if (!r) return;
    rec.current = null;
    clearInterval(r.timer);
    const blob = await r.recording.stop();
    r.stream.getTracks().forEach((t) => t.stop());
    const seconds = (performance.now() - r.started) / 1000;
    lastClip.current = blob;
    if (afterRecording(seconds, prompted) === "short-prompt") { setPrompted(true); setPhase("short"); return; }
    await store(blob);
  }, [prompted, store]);

  const begin = useCallback(async () => {
    // One recording at a time: a double click or click + Space must not open a second mic and timer.
    if (starting.current || rec.current) return;
    starting.current = true;
    let stream: MediaStream;
    let recording: Recording;
    try {
      ({ stream, recording } = await startClip(async () => (await openMic(deviceId)).stream, startRecording));
      // Left while the mic was opening (Esc): close it again; nothing is kept.
      if (!mounted.current) { recording.cancel(); stream.getTracks().forEach((t) => t.stop()); return; }
    } catch {
      setMicProblem(true);
      return;
    } finally {
      starting.current = false;
    }
    setMicProblem(false);
    const started = performance.now();
    const timer = setInterval(() => {
      const remaining = Math.max(0, DAY1.maxSeconds - Math.floor((performance.now() - started) / 1000));
      setLeft(remaining);
      if (remaining === 0) void finish();
    }, 200);
    rec.current = { recording, stream, started, timer };
    setRecordings((n) => n + 1);
    setLeft(DAY1.maxSeconds);
    setPhase("recording");
  }, [deviceId, finish]);

  const keep = useCallback(async () => {
    if (lastClip.current) await store(lastClip.current);
    else setPhase("saved");
  }, [store]);

  // Space starts and stops (design system keyboard map).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.code !== "Space" || e.repeat) return;
      if (phase === "recording") { e.preventDefault(); void finish(); }
      else if (phase === "ready") { e.preventDefault(); void begin(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [phase, begin, finish]);

  useEffect(() => {
    if (phase !== "saved") return;
    const t = setTimeout(onSaved, DAY1.savedMs);
    return () => clearTimeout(t);
  }, [phase, onSaved]);

  /** Throws the recording in progress away (nothing partial is ever stored) and goes back to "ready". */
  const discard = useCallback(() => {
    const r = rec.current;
    if (!r) return;
    rec.current = null;
    clearInterval(r.timer);
    r.recording.cancel();
    r.stream.getTracks().forEach((t) => t.stop());
    setLeft(DAY1.maxSeconds);
    setPhase("ready");
  }, []);

  // US-1.7: leaving mid-recording (a call, another tab) discards the partial clip; the step restarts on return.
  useEffect(() => {
    const onVisibility = () => { if (document.visibilityState === "hidden") discard(); };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [discard]);

  // Leaving mid-recording throws the partial clip away (US-1.7); nothing is stored before stop().
  useEffect(() => () => {
    const r = rec.current;
    if (!r) return;
    clearInterval(r.timer);
    r.recording.cancel();
    r.stream.getTracks().forEach((t) => t.stop());
  }, []);

  const mmss = `0:${String(left).padStart(2, "0")}`;
  return (
    <section className="dk-screen on" data-screen="day1">
      <Scene />
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="day1-title">
        <div className="dk-steps"><span>{copy.step}</span><span>{copy.stepRight}</span></div>
        {phase === "failed" ? (
          <>
            <h1 className="bo-flow__title" id="day1-title">That didn&apos;t save</h1>
            <p className="bo-flow__body">Your recording is safe on this device. Check your connection, then try again.</p>
            <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }} autoFocus
              onClick={() => { if (lastClip.current) void store(lastClip.current); }}>Try again</button>
          </>
        ) : phase === "short" ? (
          <>
            <h1 className="bo-flow__title" id="day1-title">Want to say a bit more?</h1>
            <p className="bo-flow__body">{copy.short}</p>
            {canRecordAgain(recordings) && (
              <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }} onClick={() => void begin()} autoFocus>
                Record again
              </button>
            )}
            <button className="bo-btn bo-btn--secondary bo-btn--block" style={{ marginTop: 10 }} onClick={() => void keep()}>
              Keep it
            </button>
          </>
        ) : (
          <>
            <h1 className="bo-flow__title" id="day1-title">{copy.title}</h1>
            <p className="bo-flow__body">{copy.body}</p>
            <ul className="bo-prompts" style={{ marginTop: 16 }}>
              <li><Icon name="person" size="sm" />Your name and where you&apos;re from</li>
              <li><Icon name="work" size="sm" />What you do</li>
              <li><Icon name="favorite" size="sm" />One thing you enjoy</li>
            </ul>
            <div className="bo-rec" style={{ marginTop: 22 }}>
              <svg className="arc" viewBox="0 0 168 168" aria-hidden="true">
                <circle className="track" cx="84" cy="84" r="80" />
                <circle className="left" cx="84" cy="84" r="80"
                  strokeDasharray={`${((ARC * left) / DAY1.maxSeconds).toFixed(1)} ${ARC}`} />
              </svg>
              {phase === "recording" ? (
                <button className="bo-rec__btn" onClick={() => void finish()} aria-label="Stop recording">
                  <span>{mmss}</span><small><Icon name="stop" fill size="sm" />Stop</small>
                </button>
              ) : (
                <button className="bo-rec__btn" onClick={() => void begin()} aria-label="Start recording"
                  disabled={phase === "saved" || phase === "saving"} autoFocus>
                  <Icon name="mic" fill /><small>Record</small>
                </button>
              )}
            </div>
            {phase === "saved" && canRecordAgain(recordings) && (
              <button className="bl-quiet-link" onClick={() => void begin()}>Record again</button>
            )}
            {micProblem && (
              <p className="bl-result" role="alert">
                <Icon name="info" size="sm" />Bolo can&apos;t hear your mic. Check it is plugged in and allowed, then try again.
              </p>
            )}
            <p className="bo-footnote" style={{ marginTop: 12 }}>
              Press <span className="dk-kbd">Space</span> to {phase === "recording" ? "stop early" : "start"}.
            </p>
          </>
        )}
      </div>
      <div className={`dk-toast${phase === "saved" ? " show" : ""}`} role="status" aria-live="polite">
        {phase === "saved" ? copy.saved : ""}
      </div>
    </section>
  );
}
