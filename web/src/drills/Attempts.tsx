"use client";
import { useEffect, useRef, useState } from "react";
import { ReadAloud } from "@/practice/ReadAloud";
import { better, TRIES, type Level } from "./drills";

/**
 * One spoken item with up to 3 tries (DRL-6). A pass (Nailed it; for say it better, Closer too: the same meaning counts)
 * offers Next; after the third try without one: "Good effort. We'll come back to this one." and it moves on by itself.
 */
export function Attempts({ reference, mode, deviceId, label, startSignal, onClip, onDone }: {
  reference: string; mode?: "upgrade"; deviceId: string | null; label: string; startSignal?: number;
  onClip?: (wav: Blob) => void; onDone: (first: Level, best: Level) => void;
}) {
  const [tries, setTries] = useState(0);
  const [first, setFirst] = useState<Level | undefined>();
  const [best, setBest] = useState<Level | undefined>();
  const [passed, setPassed] = useState(false);
  const done = useRef(false);
  const gaveUp = tries >= TRIES && !passed;

  useEffect(() => {
    if (!gaveUp || !first || !best || done.current) return;
    const t = setTimeout(() => { done.current = true; onDone(first, best); }, 1500);
    return () => clearTimeout(t);
  }, [gaveUp, first, best, onDone]);

  const outcome = (level: Level) => {
    setTries((n) => n + 1);
    setFirst((f) => f ?? level);
    setBest((b) => better(b, level));
    if (level === "NAILED" || (mode === "upgrade" && level === "CLOSER")) setPassed(true);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}>
      {!passed && !gaveUp && (
        <ReadAloud reference={reference} mode={mode} deviceId={deviceId} showSentence={false} label={tries > 0 ? "Try again" : label}
          startSignal={startSignal} onClip={onClip} onOutcome={(o) => outcome(o.level)} />
      )}
      {passed && first && best && (
        <button className="bo-btn bo-btn--primary" autoFocus onClick={() => { if (!done.current) { done.current = true; onDone(first, best); } }}>Next</button>
      )}
      {gaveUp && <p className="bo-footnote" role="status">Good effort. We&apos;ll come back to this one.</p>}
    </div>
  );
}
