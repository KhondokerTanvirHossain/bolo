"use client";
import { useEffect, useRef, useState } from "react";
import { startPcmRecording, type PcmRecording } from "@/audio/recordPcm";
import { highlight } from "@/coach/highlight";
import { Icon } from "@/scene/Scene";
import { postAttempt, RESULT, type Outcome } from "./attempts";

type Phase = "idle" | "recording" | "checking" | "done" | "error";

/**
 * Say a sentence and see how it went (US-4.3): Start → Stop (or 10 s) → "Nailed it" / "Closer" / "Let's try once
 * more", with the words to work on highlighted and their tips. One request per attempt, however fast the taps.
 */
export function ReadAloud({ reference, deviceId, showSentence = true, onOutcome }: {
  reference: string; deviceId: string | null; showSentence?: boolean; onOutcome?: (o: Outcome) => void;
}) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [error, setError] = useState<string | null>(null);
  const rec = useRef<PcmRecording | null>(null);
  const busy = useRef(false);

  useEffect(() => () => rec.current?.cancel(), []);
  useEffect(() => { setPhase("idle"); setOutcome(null); setError(null); }, [reference]);

  const check = async (wav: Blob) => {
    setPhase("checking");
    const reply = await postAttempt(wav, reference);
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
    setOutcome(null);
    setError(null);
    try {
      rec.current = await startPcmRecording(deviceId, 10_000, (wav) => { rec.current = null; void check(wav); });
      setPhase("recording");
    } catch {
      busy.current = false;
      setError("Bolo can't hear your microphone. Check it, then try again.");
      setPhase("error");
    }
  };
  const stop = async () => {
    const r = rec.current;
    if (!r) return;
    rec.current = null;
    await check(await r.stop());
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
