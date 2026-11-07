"use client";
import { useState } from "react";
import { RESULT, type Outcome } from "@/practice/attempts";
import { Icon } from "@/scene/Scene";
import { ReadAloud } from "@/practice/ReadAloud";

/** Longer than this and a Try it reading gets long and the check unreliable; the server refuses over 30 words. */
const MAX_WORDS = 20;

/**
 * US-4.3/4.4: say the better version. A sentence upgrade is judged on clarity and flow, not word for word. After 3
 * tries that aren't "Nailed it" it moves on kindly, still showing how the last one went.
 */
export function TryIt({ reference, deviceId, kind }: { reference: string; deviceId: string | null; kind: "sound" | "upgrade" }) {
  const [tries, setTries] = useState(0);
  const [nailed, setNailed] = useState(false);
  const [last, setLast] = useState<Outcome | null>(null);
  if (reference.trim().split(/\s+/).length > MAX_WORDS) return null;
  if (tries >= 3 && !nailed) {
    const result = last ? RESULT[last.level] : null;
    return (
      <div role="status" style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
        {result && <span className={result.className}><Icon name={result.icon} />{result.text}</span>}
        {last?.focus.map((f, i) => (
          <p className="bo-footnote" key={`${f.word}-${i}`} style={{ margin: 0 }}><strong>{f.word}</strong> · {f.title}: {f.tip}</p>
        ))}
        <p className="bo-footnote" style={{ textAlign: "center" }}>This one takes practice. Come back to it any time.</p>
      </div>
    );
  }
  return <ReadAloud reference={reference} deviceId={deviceId} showSentence={false} mode={kind === "upgrade" ? "upgrade" : undefined}
    onOutcome={(o) => { setLast(o); setTries((t) => t + 1); if (o.level === "NAILED") setNailed(true); }} />;
}
