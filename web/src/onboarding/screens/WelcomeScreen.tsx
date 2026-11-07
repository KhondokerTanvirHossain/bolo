"use client";
import { useEffect } from "react";
import { Scene } from "@/scene/Scene";
import { MIC_RING_SVG, WORDMARK_SMALL_SVG } from "@/scene/art";

/** Bolo's voice welcome. Until the TTS vendor is chosen (Phase 0 bake-off) it is caption-only and moves on by itself. */
export function WelcomeScreen({ onDone }: { onDone: () => void }) {
  useEffect(() => {
    const t = setTimeout(onDone, 6000);
    return () => clearTimeout(t);
  }, [onDone]);
  return (
    <section className="dk-screen on" data-screen="welcome">
      <Scene />
      <div className="dk-talk__top">
        <span style={{ display: "flex", height: 28 }} dangerouslySetInnerHTML={{ __html: WORDMARK_SMALL_SVG }} />
        <button className="bo-scenebtn" onClick={onDone} autoFocus>Skip intro</button>
      </div>
      <div className="dk-talk__cap">
        <div className="bo-caption" aria-live="polite">
          <p className="bo-caption__text">
            Hi, I&apos;m Bolo. We&apos;ll just talk, like friends. <span className="dim">No one is grading you.</span>
          </p>
        </div>
      </div>
      <div className="dk-dock" style={{ background: "transparent", WebkitBackdropFilter: "none", backdropFilter: "none" }}>
        <div className="bo-mic bo-mic--buddy" role="status" aria-label="Bolo is speaking">
          <span style={{ display: "contents" }} dangerouslySetInnerHTML={{ __html: MIC_RING_SVG }} />
          <div className="bo-mic__core"><span className="bo-icon bo-icon--lg" aria-hidden="true">graphic_eq</span></div>
        </div>
      </div>
    </section>
  );
}
