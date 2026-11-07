"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { saveClip } from "@/audio/clipStore";
import { startRecording, type Recording } from "@/audio/recorder";
import { Icon, Scene } from "@/scene/Scene";
import { afterRecording, canRecordAgain, DAY1 } from "../day1";

type Phase = "ready" | "recording" | "short" | "saved";
const ARC = 502.7;

/** "Introduce yourself, like at a new job" (prototype day1 card, US-1.6). The clip stays on this device. */
export function Day1Screen({ deviceId, onSaved }: { deviceId: string | null; onSaved: () => void }) {
  const [phase, setPhase] = useState<Phase>("ready");
  const [left, setLeft] = useState<number>(DAY1.maxSeconds);
  const [recordings, setRecordings] = useState(0);
  const [prompted, setPrompted] = useState(false);
  const [micProblem, setMicProblem] = useState(false);
  const rec = useRef<{ recording: Recording; stream: MediaStream; started: number; timer: ReturnType<typeof setInterval> } | null>(null);
  const lastClip = useRef<Blob | null>(null);

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
    await saveClip("day1", blob);
    setPhase("saved");
  }, [prompted]);

  const begin = useCallback(async () => {
    let stream: MediaStream;
    let recording: Recording;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { deviceId: deviceId ? { exact: deviceId } : undefined, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      recording = startRecording(stream);
    } catch (e) {
      console.error("day1 record failed", e instanceof Error ? `${e.name}: ${e.message}` : e);
      setMicProblem(true);
      return;
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
    if (lastClip.current) await saveClip("day1", lastClip.current);
    setPhase("saved");
  }, []);

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
    const t = setTimeout(onSaved, DAY1.savedToastMs);
    return () => clearTimeout(t);
  }, [phase, onSaved]);

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
        <div className="dk-steps"><span>Your Day 1 clip</span><span>Last step</span></div>
        {phase === "short" ? (
          <>
            <h1 className="bo-flow__title" id="day1-title">Want to say a bit more?</h1>
            <p className="bo-flow__body">A few more seconds helps you hear your progress on Day 7.</p>
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
            <h1 className="bo-flow__title" id="day1-title">Introduce yourself, like at a new job</h1>
            <p className="bo-flow__body">30 seconds. We&apos;ll play it back to you on Day 7.</p>
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
                  disabled={phase === "saved"} autoFocus>
                  <Icon name="mic" fill /><small>Record</small>
                </button>
              )}
            </div>
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
        {phase === "saved" ? "Saved. We'll play this back to you on Day 7." : ""}
      </div>
    </section>
  );
}
