"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { Icon } from "@/scene/Scene";

const BARS = [4, 6, 13, 24, 23, 10, 17, 25, 21, 26, 24, 8, 25, 6, 21, 14, 23, 13, 12, 21, 23, 23, 21, 18, 26, 10, 13, 8, 5, 4];

/**
 * A clip with a play button and a decorative wave (the prototype's bo-clip); `model` styles it as the Coach's.
 * The <audio> element follows `src`, so a card reused for the next fix plays the next fix's clip. `playRef` gets one
 * stable play function per clip (a parent may keep it, e.g. for the Space key) and is not called on every render.
 */
export function Clip({ src, label, model = false, playRef }: { src: string | null | undefined; label: string; model?: boolean;
  playRef?: (play: () => void) => void }) {
  const audio = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);

  const play = useCallback(() => {
    const a = audio.current;
    if (!a) return;
    a.currentTime = 0;
    void a.play().then(() => setPlaying(true), () => setPlaying(false));
  }, []);

  // Register once per clip; the parent's callback identity changing on its re-renders must not re-register.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { if (src) playRef?.(play); }, [play, src]);

  useEffect(() => {
    setPlaying(false);
    const a = audio.current;
    return () => { a?.pause(); };
  }, [src]);

  if (!src) return null;
  return (
    <div className={`bo-clip${model ? " bo-clip--model" : ""}`}>
      <audio ref={audio} src={src} preload="none" onEnded={() => setPlaying(false)} />
      <button className="bo-clip__play" aria-label={label} onClick={play}>
        <Icon name={playing ? "graphic_eq" : "play_arrow"} fill />
      </button>
      <div className="bo-clip__wave" aria-hidden="true">
        {BARS.map((h, i) => <i key={i} className={model ? "on" : undefined} style={{ height: h }} />)}
      </div>
    </div>
  );
}
