"use client";
import { useEffect, useRef, useState } from "react";
import { startPcmRecording, type PcmRecording } from "@/audio/recordPcm";
import { highlight } from "@/coach/highlight";
import { Icon } from "@/scene/Scene";
import { postAttempt, RESULT, type Outcome } from "./attempts";

type Phase = "idle" | "recording" | "checking" | "done" | "error";

/** The server's cap is 15 s; the recording stops itself there. */
const MAX_MS = 15_000;

/**
 * Say a sentence and see how it went (US-4.3): Start → Stop (or 15 s) → "Nailed it" / "Closer" / "Let's try once
 * more", with the words to work on highlighted and their tips. One request per attempt, however fast the taps.
 * A new sentence (or leaving) drops whatever the last one still had in flight: its recording and its result.
 */
export function ReadAloud({ reference, deviceId, showSentence = true, mode, onOutcome }: {
  reference: string; deviceId: string | null; showSentence?: boolean; mode?: "upgrade"; onOutcome?: (o: Outcome) => void;
}) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [error, setError] = useState<string | null>(null);
  const rec = useRef<PcmRecording | null>(null);
  const busy = useRef(false);
  const gen = useRef(0);

  const drop = () => {
    gen.current++;
    busy.current = false;
    rec.current?.cancel();
    rec.current = null;
  };
  useEffect(() => drop, []);
  useEffect(() => { drop(); setPhase("idle"); setOutcome(null); setError(null); }, [reference]);

  const check = async (wav: Blob, g: number) => {
    setPhase("checking");
    const reply = await postAttempt(wav, reference, mode);
    if (g !== gen.current) return;
    busy.current = false;
    if (reply.kind === "ok") {
      setOutcome(reply.outcome);
      setPhase("done");
      onOutcome?.(reply.outcome);
    } else {
      setError(reply.kind === "busy" ? "Bolo is busy for a moment. Try again in a few seconds."
        : reply.kind === "slow-down" ? "Take a short break, then try again." : "That didn't work. Try again.");
      setPhase("error");
    }
  };

  const start = async () => {
    if (busy.current) return;
    busy.current = true;
    const g = gen.current;
    setOutcome(null);
    setError(null);
    try {
      const r = await startPcmRecording(deviceId, MAX_MS, (wav) => {
        if (g !== gen.current) return;
        rec.current = null;
        void check(wav, g);
      });
      if (g !== gen.current) { r.cancel(); return; }
      rec.current = r;
      setPhase("recording");
    } catch {
      if (g !== gen.current) return;
      busy.current = false;
      setError("Bolo can't hear your microphone. Check it, then try again.");
      setPhase("error");
    }
  };
  const stop = async () => {
    const r = rec.current;
    if (!r) return;
    rec.current = null;
    const g = gen.current;
    await check(await r.stop(), g);
  };

  const focusWords = outcome?.focus.map((f) => f.word) ?? [];
  const result = outcome ? RESULT[outcome.level] : null;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}>
      {showSentence && <p className="title-1" style={{ margin: 0, textAlign: "center" }}>{highlight(reference, focusWords, "bo-said")}</p>}
      {phase === "recording"
        ? <button className="bo-btn bo-btn--primary" style={{ minWidth: 220 }} onClick={() => void stop()}><Icon name="stop" fill />Stop recording</button>
        : <button className="bo-btn bo-btn--primary" style={{ minWidth: 220 }} disabled={phase === "checking"} onClick={() => void start()}>
            <Icon name="mic" fill />{showSentence ? "Start recording" : "Try it"}</button>}
      {phase === "checking" && <p className="bo-footnote" role="status">Listening back…</p>}
      {result && outcome && (
        <div role="status" style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
          <span className={result.className}><Icon name={result.icon} />{result.text}</span>
          {!outcome.heard && <p className="bo-footnote">We couldn&apos;t hear that. Let&apos;s try once more.</p>}
          {outcome.focus.map((f, i) => (
            <p className="bo-footnote" key={`${f.word}-${i}`} style={{ margin: 0 }}><strong>{f.word}</strong> · {f.title}: {f.tip}</p>
          ))}
        </div>
      )}
      {error && <p className="bo-footnote" role="alert">{error}</p>}
    </div>
  );
}
