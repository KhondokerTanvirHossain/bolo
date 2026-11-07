"use client";
import { useEffect, useRef, useState } from "react";
import { loadClip } from "@/audio/clipStore";
import { Icon, Scene } from "@/scene/Scene";
import { WORDMARK_SMALL_SVG } from "@/scene/art";

/** Where the first conversation will start (US-1.6 → TLK-1). Until Bolo's voice is connected, it plays the Day 1 clip back. */
export function FirstTalkScreen() {
  const [url, setUrl] = useState<string | null>(null);
  const audio = useRef<HTMLAudioElement>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    void loadClip("day1").then((blob) => {
      if (!blob) return;
      objectUrl = URL.createObjectURL(blob);
      setUrl(objectUrl);
    });
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, []);

  return (
    <section className="dk-screen on" data-screen="talk">
      <Scene />
      <div className="dk-talk__top">
        <span style={{ display: "flex", height: 28 }} dangerouslySetInnerHTML={{ __html: WORDMARK_SMALL_SVG }} />
      </div>
      <div className="dk-talk__cap">
        <div className="bo-caption" aria-live="polite">
          <p className="bo-caption__text">
            Your first talk with Bolo starts here once Bolo&apos;s voice is connected.{" "}
            <span className="dim">Your Day 1 clip is saved on this device.</span>
          </p>
        </div>
      </div>
      <div className="dk-dock">
        {url ? (
          <>
            <button className="bo-btn bo-btn--primary" onClick={() => void audio.current?.play()}>
              <Icon name="play_arrow" fill />Play my Day 1 clip
            </button>
            <audio ref={audio} src={url} preload="auto" data-testid="day1-audio" />
          </>
        ) : (
          <p className="bo-footnote" style={{ color: "var(--on-scene-secondary)" }}>No Day 1 clip on this device yet.</p>
        )}
      </div>
    </section>
  );
}
