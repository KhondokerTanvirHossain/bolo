"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { Attempts } from "./Attempts";
import { sayUrl, type Level, type ShadowDrill as Shadow } from "./drills";
import { play } from "./play";

/**
 * Shadowing (DRL-3, US-5.3): hear the model, then the mic opens by itself and the learner repeats it; then the model and
 * their take back to back. The turtle plays the model at 0.75×.
 */
export function ShadowDrill({ drill, talkId, deviceId, onFinish }: { drill: Shadow; talkId: string; deviceId: string | null;
  onFinish: (first?: Level, best?: Level) => void }) {
  const [signal, setSignal] = useState(0);
  const [mine, setMine] = useState<string | null>(null);
  const model = sayUrl(talkId, drill.sentence);
  useEffect(() => () => { if (mine) URL.revokeObjectURL(mine); }, [mine]);

  const hear = async (rate = 1) => { await play(model, rate); setSignal((n) => n + 1); };
  const both = async () => { await play(model); if (mine) await play(mine); };

  return (
    <div className="dk-drill" data-drill="shadow">
      <p className="bo-eyebrow bo-eyebrow--coach"><span className="bo-coach-mark">C</span>{drill.title} · Shadow it</p>
      <p className="title-1" style={{ margin: "8px 0 12px", textAlign: "center" }}>{drill.sentence}</p>
      <div style={{ display: "flex", gap: 10, justifyContent: "center" }}>
        <button className="bo-btn bo-btn--secondary" onClick={() => void hear()}><Icon name="volume_up" />Hear it, then say it</button>
        <button className="bo-btn bo-btn--quiet-neutral" aria-label="Hear it slower" onClick={() => void play(model, 0.75)}><Icon name="slow_motion_video" />Slower</button>
      </div>
      <Attempts reference={drill.sentence} deviceId={deviceId} label="Say it" startSignal={signal}
        onClip={(wav) => setMine(URL.createObjectURL(wav))} onDone={(f, b) => onFinish(f, b)} />
      {mine && <button className="bo-btn bo-btn--quiet-neutral" onClick={() => void both()}><Icon name="compare_arrows" />Hear the model, then you</button>}
    </div>
  );
}
