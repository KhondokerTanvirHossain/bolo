"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { loadClip } from "@/audio/clipStore";
import { CoachFlow } from "@/coach/CoachFlow";
import { fetchHome, type Home } from "@/home/home";
import { HomeScreen } from "@/home/HomeScreen";
import { PracticeScreen } from "@/practice/PracticeScreen";
import type { LearnerView } from "@/api/browser";
import { afterDebrief, loadLoop, saveAnswered, saveLoop, seenSeeYou, type Loop } from "@/account/loop";
import { GuestLimit } from "@/account/GuestLimit";
import { FreeLimit } from "@/plan/FreeLimit";
import { SaveProgress } from "@/account/SaveProgress";
import { fetchAuthConfig, fetchMe } from "@/account/session";
import { AppShell, type Tab } from "@/me/AppShell";
import { HistoryScreen } from "@/progress/HistoryScreen";
import { ProgressScreen } from "@/progress/ProgressScreen";
import { MeScreen } from "@/me/MeScreen";
import { PrivacyScreen } from "@/me/PrivacyScreen";
import { clearThisBrowser } from "@/account/session";
import { TalkScreen } from "@/talk/TalkScreen";
import { useTalk } from "@/talk/useTalk";

type View = { kind: "home" } | { kind: "practice"; pattern?: string } | { kind: "feedback"; talkId: string } | { kind: "me" } | { kind: "privacy" }
  | { kind: "progress" } | { kind: "history" } | { kind: "session"; talkId: string; from: "progress" | "history" };

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
  const [me, setMe] = useState<LearnerView | null>(null);
  /** null until /api/auth/config answers. */
  const [signInOn, setSignInOn] = useState<boolean | null>(null);
  const [loop, setLoopState] = useState<Loop>(() => loadLoop());
  const [seeYou, setSeeYou] = useState(false);
  const setLoop = useCallback((next: (l: Loop) => Loop) => setLoopState((l) => { const n = next(l); saveLoop(n); return n; }), []);
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
    void fetchMe().then((m) => {
      if (cancelled || !m) return;
      setMe(m);
      if (m.settings) setCaptions(m.settings.captions);
    });
    return () => { cancelled = true; };
  }, [idle, view.kind]);

  useEffect(() => { void fetchAuthConfig().then((c) => setSignInOn(c.enabled)); }, []);
  // Me and Privacy always show the latest (a privacy choice made there must show when coming back).
  useEffect(() => {
    if (view.kind !== "me" && view.kind !== "privacy") return;
    let cancelled = false;
    void fetchMe().then((m) => { if (!cancelled && m) setMe(m); });
    return () => { cancelled = true; };
  }, [view.kind]);

  // "See you tomorrow" once, on the Home after the first loop (US-6.8).
  useEffect(() => {
    if (idle && view.kind === "home" && loop.seeYou && !loop.pendingSave) {
      setSeeYou(true);
      setLoop(seenSeeYou);
    }
  }, [idle, view.kind, loop.seeYou, loop.pendingSave, setLoop]);

  const guest = me?.kind === "guest";
  // No save step for an account, or while sign-in is off: the pending prompt is simply answered.
  useEffect(() => {
    if (loop.pendingSave && me && signInOn !== null && (!guest || !signInOn)) setLoop(saveAnswered);
  }, [loop.pendingSave, me, signInOn, guest, setLoop]);
  const coachReady = useCallback(() => setLoop((l) => afterDebrief(l, { guest: me?.kind !== "account" })), [setLoop, me]);
  const signedIn = useCallback((learner: LearnerView) => { setMe(learner); setLoop(saveAnswered); }, [setLoop]);
  const toHome = useCallback(() => setView({ kind: "home" }), []);
  const talk = useCallback(() => { setSeeYou(false); setView({ kind: "home" }); void conversation.start(); }, [conversation]);

  // ACC-2: the 4th guest talk was refused; Home with the sign-in sheet. Not now leaves Home as it was.
  if (conversation.state.phase === "needs_sign_in") {
    return (
      <>
        <HomeScreen home={home} hasDay1={url !== null} onTalk={talk} onPractice={() => {}} onFeedback={() => {}} onSettings={() => {}} onPlayDay1={() => {}} />
        <GuestLimit me={me} onClose={conversation.reset} onSignedIn={(l) => { signedIn(l); conversation.reset(); }} />
      </>
    );
  }
  // US-8.7: no free talks left; Home with the sheet saying when they come back. Practice stays free.
  if (conversation.state.phase === "out_of_talks") {
    return (
      <>
        <HomeScreen home={home} hasDay1={url !== null} onTalk={talk} onPractice={() => {}} onFeedback={() => {}} onSettings={() => {}} onPlayDay1={() => {}} />
        <FreeLimit freeTalks={home?.freeTalks ?? null} onClose={conversation.reset} />
      </>
    );
  }
  if (!idle) {
    return <TalkScreen state={conversation.state} deviceId={deviceId} onDone={conversation.done} onEnd={conversation.end}
      onTalkAgain={() => void conversation.start()} onLeave={conversation.reset} onPause={conversation.pause}
      onResume={conversation.resume} onRepeat={conversation.repeat} onKeepSlower={conversation.answerKeepSlower}
      captionsDefault={captions} onCoachReady={coachReady}
      onPractise={(pattern) => { conversation.reset(); setView({ kind: "practice", pattern }); }} />;
  }
  if (view.kind === "practice") return <PracticeScreen deviceId={deviceId} onBack={toHome} initialPattern={view.pattern} />;
  if (view.kind === "feedback") {
    return <CoachFlow talkId={view.talkId} deviceId={deviceId} onTalkAgain={talk} onDone={toHome} onNoDebrief={toHome}
      onPractise={(pattern) => setView({ kind: "practice", pattern })} />;
  }
  const toTab = (t: Tab) => { setSeeYou(false); setView({ kind: t === "talk" ? "home" : t }); };
  if (view.kind === "session") {
    return <CoachFlow talkId={view.talkId} deviceId={deviceId} history onTalkAgain={talk} onDone={() => setView({ kind: view.from })}
      onNoDebrief={() => setView({ kind: view.from })} onPractise={(pattern) => setView({ kind: "practice", pattern })} />;
  }
  if (view.kind === "progress" || view.kind === "history") {
    const open = (talkId: string) => setView({ kind: "session", talkId, from: view.kind as "progress" | "history" });
    return (
      <AppShell tab="progress" onTab={toTab}>
        {view.kind === "progress"
          ? <ProgressScreen onOpen={open} onSeeAll={() => setView({ kind: "history" })} onTalk={talk}
              onPractise={(pattern) => setView({ kind: "practice", pattern })} />
          : <HistoryScreen onOpen={open} onTalk={talk} />}
      </AppShell>
    );
  }
  if (view.kind === "me" || view.kind === "privacy") {
    return (
      <AppShell tab="me" onTab={toTab}>
        {view.kind === "me"
          ? <MeScreen me={me} onPrivacy={() => setView({ kind: "privacy" })} onSignedIn={signedIn} />
          : <PrivacyScreen keep={me?.consent?.keepRecordings === true}
              onDeleted={() => void clearThisBrowser().then(() => window.location.assign("/"))} />}
      </AppShell>
    );
  }
  // US-6.8 / US-8.2: a guest's save step comes before Home (also when it was missed last time). With sign-in off, skip it.
  if (loop.pendingSave) {
    if (guest && signInOn === true) {
      return <SaveProgress me={me} hasDay1={url !== null} onSignedIn={signedIn} onStayGuest={() => setLoop(saveAnswered)} />;
    }
  }
  return (
    <>
      <HomeScreen home={home} note={seeYou ? "See you tomorrow. Bolo will be here." : undefined} hasDay1={url !== null} onTalk={talk}
        onPractice={(pattern) => setView({ kind: "practice", pattern })}
        onFeedback={(talkId) => setView({ kind: "feedback", talkId })}
        onSettings={() => setView({ kind: "me" })}
        onPlayDay1={() => void audio.current?.play()} />
      {url && <audio ref={audio} src={url} preload="auto" data-testid="day1-audio" />}
    </>
  );
}
