"use client";
import { useEffect, useRef, useState } from "react";
import { loadClip } from "@/audio/clipStore";
import { Icon, Scene } from "@/scene/Scene";
import { WORDMARK_SMALL_SVG } from "@/scene/art";
import { TalkScreen } from "@/talk/TalkScreen";
import { useTalk } from "@/talk/useTalk";

/** Where talks start (US-1.6 → TLK-1): "Start talking" opens a talk with Bolo; the Day 1 clip can still be played back. */
export function FirstTalkScreen({ deviceId }: { deviceId: string | null }) {
  const [url, setUrl] = useState<string | null>(null);
  const conversation = useTalk(deviceId);
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

  if (conversation.state.phase !== "idle") {
    return <TalkScreen state={conversation.state} onDone={conversation.done} onEnd={conversation.end}
      onTalkAgain={() => void conversation.start()} onLeave={conversation.reset} />;
  }

  return (
    <section className="dk-screen on" data-screen="talk">
      <Scene />
      <div className="dk-talk__top">
        <span style={{ display: "flex", height: 28 }} dangerouslySetInnerHTML={{ __html: WORDMARK_SMALL_SVG }} />
      </div>
      <div className="dk-talk__cap">
        <div className="bo-caption" aria-live="polite">
          <p className="bo-caption__text">
            Ready when you are. Bolo talks out loud, so turn your sound on.
            {url && <>{" "}<span className="dim">Your Day 1 clip is saved on this device.</span></>}
          </p>
        </div>
      </div>
      <div className="dk-dock">
        <button className="bo-btn bo-btn--primary" onClick={() => void conversation.start()} autoFocus>
          <Icon name="mic" fill />Start talking
        </button>
        {url && (
          <>
            <button className="bo-scenebtn" onClick={() => void audio.current?.play()}>
              <Icon name="play_arrow" fill />Play my Day 1 clip
            </button>
            <audio ref={audio} src={url} preload="auto" data-testid="day1-audio" />
          </>
        )}
      </div>
    </section>
  );
}
