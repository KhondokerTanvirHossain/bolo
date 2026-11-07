"use client";
import { useEffect, useRef, useState } from "react";
import { Icon } from "@/scene/Scene";

const BARS = [4, 6, 13, 24, 23, 10, 17, 25, 21, 26, 24, 8, 25, 6, 21, 14, 23, 13, 12, 21, 23, 23, 21, 18, 26, 10, 13, 8, 5, 4];

/** A clip with a play button and a decorative wave (the prototype's bo-clip). `model` styles it as the Coach's. */
export function Clip({ src, label, model = false, playRef }: { src: string | null | undefined; label: string; model?: boolean;
  playRef?: (play: () => void) => void }) {
  const audio = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);

  useEffect(() => () => { audio.current?.pause(); }, []);
  const play = () => {
    if (!src) return;
    if (!audio.current) {
      audio.current = new Audio(src);
      audio.current.onended = () => setPlaying(false);
    }
    audio.current.currentTime = 0;
    void audio.current.play().then(() => setPlaying(true), () => setPlaying(false));
  };
  useEffect(() => { playRef?.(play); });

  if (!src) return null;
  return (
    <div className={`bo-clip${model ? " bo-clip--model" : ""}`}>
      <button className="bo-clip__play" aria-label={label} onClick={play}>
        <Icon name={playing ? "graphic_eq" : "play_arrow"} fill />
      </button>
      <div className="bo-clip__wave" aria-hidden="true">
        {BARS.map((h, i) => <i key={i} className={model ? "on" : undefined} style={{ height: h }} />)}
      </div>
    </div>
  );
}
