"use client";
import { useEffect, useRef, useState } from "react";
import { Icon } from "@/scene/Scene";
import { Attempts } from "./Attempts";
import { sayUrl, type Level, type ShadowDrill as Shadow } from "./drills";
import { play } from "./play";

/**
 * Shadowing (DRL-3, US-5.3): the model plays, then the mic opens by itself and the learner repeats it; after each try,
 * their take and the model play back to back. The turtle plays the model at 0.75×.
 */
export function ShadowDrill({ drill, talkId, deviceId, onFinish }: { drill: Shadow; talkId: string; deviceId: string | null;
  onFinish: (first?: Level, best?: Level) => void }) {
  const [signal, setSignal] = useState(0);
  const [mine, setMine] = useState<string | null>(null);
  const [silent, setSilent] = useState(false);
  const model = sayUrl(talkId, drill.sentence);
  const mineRef = useRef<string | null>(null);
  useEffect(() => () => { if (mineRef.current) URL.revokeObjectURL(mineRef.current); }, []);

  /** The model, then the mic (only when the model really played). */
  const hear = async (rate = 1) => {
    const ok = await play(model, rate);
    setSilent(!ok);
    if (ok) setSignal((n) => n + 1);
  };
  // Starts with the model straight away (the drill opened from a tap).
  useEffect(() => { void hear(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const both = async () => { if (mineRef.current) await play(mineRef.current); await play(model); };

  return (
    <div className="dk-drill" data-drill="shadow">
      <p className="bo-eyebrow bo-eyebrow--coach"><span className="bo-coach-mark">C</span>{drill.title} · Shadow it</p>
      <p className="title-1" style={{ margin: "8px 0 12px", textAlign: "center" }}>{drill.sentence}</p>
      <div style={{ display: "flex", gap: 10, justifyContent: "center" }}>
        <button className="bo-btn bo-btn--secondary" onClick={() => void hear()}><Icon name="volume_up" />Hear it, then say it</button>
        <button className="bo-btn bo-btn--quiet-neutral" aria-label="Hear it slower" onClick={() => void play(model, 0.75)}><Icon name="slow_motion_video" />Slower</button>
      </div>
      {silent && <p className="bo-footnote" role="status">Couldn&apos;t play the model. Check your connection, then try again.</p>}
      <Attempts reference={drill.sentence} deviceId={deviceId} label="Say it" startSignal={signal}
        onClip={(wav) => {
          if (mineRef.current) URL.revokeObjectURL(mineRef.current);
          mineRef.current = URL.createObjectURL(wav);
          setMine(mineRef.current);
        }}
        onTry={() => void both()} onDone={(f, b) => onFinish(f, b)} />
      {mine && <button className="bo-btn bo-btn--quiet-neutral" onClick={() => void both()}><Icon name="compare_arrows" />Hear you, then the model</button>}
    </div>
  );
}
