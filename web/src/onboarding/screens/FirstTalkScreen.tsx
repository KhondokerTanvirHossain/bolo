"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { loadClip } from "@/audio/clipStore";
import { CoachFlow } from "@/coach/CoachFlow";
import { fetchHome, type Home } from "@/home/home";
import { HomeScreen } from "@/home/HomeScreen";
import { PracticeScreen } from "@/practice/PracticeScreen";
import { fetchSettings } from "@/settings/settings";
import { SettingsCard } from "@/settings/SettingsCard";
import { TalkScreen } from "@/talk/TalkScreen";
import { useTalk } from "@/talk/useTalk";

type View = { kind: "home" } | { kind: "practice"; pattern?: string } | { kind: "feedback"; talkId: string } | { kind: "settings" };

/**
 * After onboarding: Home (US-6.9) with its one next step, and from there a talk, the Coach's feedback, practice or
 * Settings. Home is read again whenever the learner comes back to it.
 */
export function FirstTalkScreen({ deviceId }: { deviceId: string | null }) {
  const [url, setUrl] = useState<string | null>(null);
  const conversation = useTalk(deviceId);
  const [view, setView] = useState<View>({ kind: "home" });
  const [home, setHome] = useState<Home | null>(null);
  const [captions, setCaptions] = useState(true);
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

  const idle = conversation.state.phase === "idle";
  useEffect(() => {
    if (!idle || view.kind !== "home") return;
    let cancelled = false;
    void fetchHome().then((h) => { if (!cancelled) setHome(h); });
    void fetchSettings().then((s) => { if (!cancelled) setCaptions(s.captions); });
    return () => { cancelled = true; };
  }, [idle, view.kind]);

  const toHome = useCallback(() => setView({ kind: "home" }), []);
  const talk = useCallback(() => { setView({ kind: "home" }); void conversation.start(); }, [conversation]);

  if (!idle) {
    return <TalkScreen state={conversation.state} deviceId={deviceId} onDone={conversation.done} onEnd={conversation.end}
      onTalkAgain={() => void conversation.start()} onLeave={conversation.reset} onPause={conversation.pause}
      onResume={conversation.resume} onRepeat={conversation.repeat} onKeepSlower={conversation.answerKeepSlower}
      captionsDefault={captions} />;
  }
  if (view.kind === "practice") return <PracticeScreen deviceId={deviceId} onBack={toHome} initialPattern={view.pattern} />;
  if (view.kind === "feedback") {
    return <CoachFlow talkId={view.talkId} deviceId={deviceId} onTalkAgain={talk} onDone={toHome} onNoDebrief={toHome} />;
  }
  return (
    <>
      <HomeScreen home={home} hasDay1={url !== null} onTalk={talk}
        onPractice={(pattern) => setView({ kind: "practice", pattern })}
        onFeedback={(talkId) => setView({ kind: "feedback", talkId })}
        onSettings={() => setView({ kind: "settings" })}
        onPlayDay1={() => void audio.current?.play()} />
      {view.kind === "settings" && <SettingsCard onClose={toHome} />}
      {url && <audio ref={audio} src={url} preload="auto" data-testid="day1-audio" />}
    </>
  );
}
