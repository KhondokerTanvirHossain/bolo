"use client";
import { useState } from "react";
import { Attempts } from "./Attempts";
import { better, hideKeyWords, type Level, type SayBetterDrill as SayBetter } from "./drills";

/**
 * Say it better (DRL-4, US-5.4): the learner's own upgraded sentence three times, with less help each round: read it,
 * then the key words hidden, then from memory. The original stays greyed above. Saying the same meaning counts.
 */
export function SayBetterDrill({ drill, deviceId, onFinish }: { drill: SayBetter; deviceId: string | null;
  onFinish: (first?: Level, best?: Level) => void }) {
  const [round, setRound] = useState(1);
  const [first, setFirst] = useState<Level | undefined>();
  const [best, setBest] = useState<Level | undefined>();
  const shown = hideKeyWords(drill.better, drill.keyWords, round);
  return (
    <div className="dk-drill" data-drill="sayBetter">
      <p className="bo-eyebrow bo-eyebrow--coach"><span className="bo-coach-mark">C</span>{drill.title} · Round {round} of 3</p>
      <p className="bo-footnote" style={{ margin: "8px 0 0", color: "var(--ink-tertiary)" }}>You said: {drill.said}</p>
      <p className="title-1" style={{ margin: "8px 0 12px", textAlign: "center" }}>{shown ?? "Say it from memory."}</p>
      <Attempts key={round} reference={drill.better} mode="upgrade" deviceId={deviceId} label="Say it" onDone={(f, b) => {
        const nf = first ?? f;
        const nb = better(best, b);
        setFirst(nf);
        setBest(nb);
        if (round < 3) setRound(round + 1); else onFinish(nf, nb);
      }} />
    </div>
  );
}
