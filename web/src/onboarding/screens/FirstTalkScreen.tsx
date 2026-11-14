"use client";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { loadClip } from "@/audio/clipStore";
import { CoachFlow } from "@/coach/CoachFlow";
import { fetchHome, type Home } from "@/home/home";
import { HomeDashboard } from "@/home/HomeDashboard";
import { SideCards, useFreeLine } from "@/home/SideCards";
import { planName } from "@/plan/freeTalks";
import { RecordIntro } from "@/voice/RecordIntro";
import { DrillSession } from "@/drills/DrillSession";
import { PracticeScreen } from "@/practice/PracticeScreen";
import type { LearnerView } from "@/api/browser";
import { afterDebrief, loadLoop, reminderAnswered, saveAnswered, saveLoop, seenSeeYou, signedInAfterFirstLoop, type Loop } from "@/account/loop";
import { ReminderStep, type ReminderResult } from "@/reminder/ReminderStep";
import { ReminderSettings } from "@/reminder/ReminderSettings";
import { ReminderTurnOn } from "@/reminder/ReminderTurnOn";
import { useReminderOpen } from "@/reminder/useReminderOpen";
import { useTurnOn } from "@/reminder/useTurnOn";
import { formatTime } from "@/reminder/slots";
import { GuestLimit } from "@/account/GuestLimit";
import { FreeLimit } from "@/plan/FreeLimit";
import { Resting } from "@/plan/Resting";
import { SaveProgress } from "@/account/SaveProgress";
import { fetchAuthConfig, fetchMe } from "@/account/session";
import { AppShell, type Tab } from "@/me/AppShell";
import { HistoryScreen } from "@/progress/HistoryScreen";
import { ProgressScreen } from "@/progress/ProgressScreen";
import { MeScreen } from "@/me/MeScreen";
import { InviteScreen } from "@/referral/InviteScreen";
import { fetchReferral, markRewardSeen, rewardNote, type Referral } from "@/referral/referral";
import { PrivacyScreen } from "@/me/PrivacyScreen";
import { MemoryScreen } from "@/memory/MemoryScreen";
import { VoiceMoodScreen } from "@/voice/VoiceMoodScreens";
import { clearThisBrowser } from "@/account/session";
import { TalkScreen } from "@/talk/TalkScreen";
import { useTalk } from "@/talk/useTalk";

type View = { kind: "home" } | { kind: "practice"; pattern?: string } | { kind: "feedback"; talkId: string } | { kind: "me" } | { kind: "privacy" } | { kind: "invite" } | { kind: "memory" } | { kind: "voice" } | { kind: "mood" } | { kind: "answer" }
  | { kind: "progress" } | { kind: "history" } | { kind: "session"; talkId: string; from: "progress" | "history" }
  | { kind: "intro"; day: number } | { kind: "drills"; talkId: string } | { kind: "reminder" } | { kind: "reminderSettings" };

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
  /** PRG-5: after notifications were blocked, Home says when to come back instead. */
  const [reminderNote, setReminderNote] = useState<string | null>(null);
  useReminderOpen();
  const setLoop = useCallback((next: (l: Loop) => Loop) => setLoopState((l) => { const n = next(l); saveLoop(n); return n; }), []);
  const audio = useRef<HTMLAudioElement>(null);
  const freeLine = useFreeLine(home?.freeTalks);
  /** P2d: the invite offer and any month earned (accounts only). */
  const [referral, setReferral] = useState<Referral | null>(null);
  const rewardLine = referral ? rewardNote(referral) : null;

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
      if (m.kind === "account") void fetchReferral().then((r) => { if (!cancelled) setReferral(r); });
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
    if (idle && view.kind === "home" && loop.seeYou && !loop.pendingSave && !loop.askReminder) {
      setSeeYou(true);
      setLoop(seenSeeYou);
    }
  }, [idle, view.kind, loop.seeYou, loop.pendingSave, loop.askReminder, setLoop]);

  // P2d: the month earned is marked seen only when Home actually shows it ("See you tomorrow" takes the note first).
  const rewardShown = view.kind === "home" && idle && !seeYou && !!rewardLine;
  useEffect(() => {
    if (rewardShown) void markRewardSeen();
  }, [rewardShown]);

  const guest = me?.kind === "guest";
  // No save step for an account, or while sign-in is off: the pending prompt is simply answered.
  useEffect(() => {
    if (loop.pendingSave && me && signInOn !== null && (!guest || !signInOn)) setLoop(saveAnswered);
  }, [loop.pendingSave, me, signInOn, guest, setLoop]);
  const coachReady = useCallback(() => setLoop((l) => afterDebrief(l, { guest: me?.kind !== "account" })), [setLoop, me]);
  const signedIn = useCallback((learner: LearnerView) => { setMe(learner); setLoop((l) => signedInAfterFirstLoop(saveAnswered(l))); }, [setLoop]);
  /** The reminder step answered (first loop or Home's card): blocked notifications leave a "come back" note on Home. */
  const reminderDone = useCallback((r: ReminderResult) => {
    setLoop(reminderAnswered);
    if (r.kind === "denied" && r.time) setReminderNote(`Come back around ${formatTime(r.time)}`);
    setView({ kind: "home" });
  }, [setLoop]);
  const account = me?.kind === "account";
  // Reminders are for accounts: a guest's pending step (sign-in off) is simply answered.
  useEffect(() => { if (loop.askReminder && me && me.kind !== "account") setLoop(reminderAnswered); }, [loop.askReminder, me, setLoop]);
  const [turnOn, closeTurnOn] = useTurnOn(idle && view.kind === "home" && account && !loop.askReminder);
  const toHome = useCallback(() => setView({ kind: "home" }), []);
  const talk = useCallback(() => { setSeeYou(false); setReminderNote(null); setView({ kind: "home" }); void conversation.start(); }, [conversation]);

  const toTab = (t: Tab) => { setSeeYou(false); setReminderNote(null); setView({ kind: t === "talk" ? "home" : t }); };
  const lastTalk = home?.card && "talkId" in home.card ? home.card.talkId : undefined;
  const foot = <SideCards freeLine={freeLine} plan={home?.freeTalks ? planName(home.freeTalks) : null} help={{ view: view.kind, talkId: lastTalk }} />;
  const minutes = home?.freeTalks ? 5 : (me?.profile?.talkMinutes ?? 5);
  /** The dashboard Home (P1f) inside the shell; {@code sheet} sits over it and turns its Space key off. */
  const homePage = (sheet?: ReactNode) => (
    <>
      {/* Behind a sheet, Home can't be reached by keyboard or click (the sheet keeps focus; Esc closes it). */}
      <div inert={sheet ? true : undefined} style={{ display: "contents" }}>
      <AppShell tab="talk" onTab={toTab} foot={foot}>
        <HomeDashboard home={home} name={me?.account?.name} minutes={minutes} freeLine={freeLine} note={reminderNote ?? (seeYou ? "See you tomorrow. Bolo will be here." : rewardLine ?? undefined)}
          hasDay1={url !== null} keysOn={!sheet} onTalk={talk}
          onPractice={(pattern) => setView({ kind: "practice", pattern })}
          onDrills={(talkId) => setView({ kind: "drills", talkId })}
          onFeedback={(talkId) => setView({ kind: "feedback", talkId })}
          onProgress={() => toTab("progress")}
          onIntro={(day) => setView({ kind: "intro", day })}
          onReminder={account ? () => setView({ kind: "reminder" }) : undefined}
          onPlayDay1={() => void audio.current?.play()} />
      </AppShell>
      </div>
      {url && <audio ref={audio} src={url} preload="auto" data-testid="day1-audio" />}
      {sheet}
    </>
  );

  // ACC-2: the 4th guest talk was refused; Home with the sign-in sheet. Not now leaves Home as it was.
  if (conversation.state.phase === "needs_sign_in") {
    return homePage(<GuestLimit me={me} onClose={conversation.reset} onSignedIn={(l) => { signedIn(l); conversation.reset(); }} />);
  }
  // US-8.7: no free talks left; Home with the sheet saying when they come back. Practice stays free.
  if (conversation.state.phase === "resting") return homePage(<Resting onClose={conversation.reset} />);
  if (conversation.state.phase === "out_of_talks") {
    return homePage(<FreeLimit freeTalks={home?.freeTalks ?? null} onClose={conversation.reset}
      onInvite={referral?.offer ? () => { conversation.reset(); setView({ kind: "invite" }); } : undefined} />);
  }
  if (!idle) {
    return <TalkScreen state={conversation.state} deviceId={deviceId} onDone={conversation.done} onEnd={conversation.end}
      onTalkAgain={() => void conversation.start()} onLeave={conversation.reset} onPause={conversation.pause}
      onResume={conversation.resume} onRepeat={conversation.repeat} onKeepSlower={conversation.answerKeepSlower}
      captionsDefault={captions} onCoachReady={coachReady}
      talkView={me?.settings?.talkView ?? "chat"} level={conversation.level}
      onPractise={(pattern, talkId) => { conversation.reset(); setView(talkId ? { kind: "drills", talkId } : { kind: "practice", pattern }); }} />;
  }
  // Home's "Get a daily reminder" card (PRG-5): the same step as after the first talk.
  if (view.kind === "reminder") return <ReminderStep onDone={reminderDone} />;
  // Practise (3 min) (DRL-1): drills from that talk's fixes; Done and Close go Home.
  if (view.kind === "drills") {
    return <DrillSession talkId={view.talkId} learnerId={me?.learnerId ?? "guest"} deviceId={deviceId} onDone={toHome} onPractiseSounds={() => setView({ kind: "practice" })} />;
  }
  if (view.kind === "intro") {
    return <RecordIntro day={view.day} learnerId={me?.learnerId ?? "guest"} deviceId={deviceId}
      onDone={() => setView({ kind: "progress" })} onBack={toHome} />;
  }
  if (view.kind === "practice") return <PracticeScreen deviceId={deviceId} onBack={toHome} initialPattern={view.pattern} />;
  if (view.kind === "feedback") {
    return <CoachFlow talkId={view.talkId} deviceId={deviceId} onTalkAgain={talk} onDone={toHome} onNoDebrief={toHome}
      onPractise={(pattern, talkId) => setView(talkId ? { kind: "drills", talkId } : { kind: "practice", pattern })} />;
  }
  if (view.kind === "session") {
    return <CoachFlow talkId={view.talkId} deviceId={deviceId} history onTalkAgain={talk} onDone={() => setView({ kind: view.from })}
      onNoDebrief={() => setView({ kind: view.from })} onPractise={(pattern, talkId) => setView(talkId ? { kind: "drills", talkId } : { kind: "practice", pattern })} />;
  }
  if (view.kind === "progress" || view.kind === "history") {
    const open = (talkId: string) => setView({ kind: "session", talkId, from: view.kind as "progress" | "history" });
    return (
      <AppShell tab="progress" onTab={toTab} foot={foot}>
        {view.kind === "progress"
          ? <ProgressScreen learnerId={me?.learnerId ?? null} onOpen={open} onSeeAll={() => setView({ kind: "history" })} onTalk={talk}
              onRecordIntro={(day) => setView({ kind: "intro", day })}
              onPractise={(pattern) => setView({ kind: "practice", pattern })} />
          : <HistoryScreen onOpen={open} onTalk={talk} />}
      </AppShell>
    );
  }
  if (view.kind === "me" || view.kind === "privacy" || view.kind === "invite" || view.kind === "memory" || view.kind === "voice" || view.kind === "mood" || view.kind === "answer" || view.kind === "reminderSettings") {
    return (
      <AppShell tab="me" onTab={toTab} foot={foot}>
        {view.kind === "invite" ? <InviteScreen onBack={() => setView({ kind: "me" })} />
          : view.kind === "memory" ? <MemoryScreen onBack={() => setView({ kind: "me" })} />
          : view.kind === "reminderSettings" ? <ReminderSettings onBack={() => setView({ kind: "me" })} />
          : view.kind === "voice" || view.kind === "mood" || view.kind === "answer" ? <VoiceMoodScreen kind={view.kind} onBack={() => setView({ kind: "me" })} />
          : view.kind === "me"
          ? <MeScreen me={me} onPrivacy={() => setView({ kind: "privacy" })} onSignedIn={signedIn}
              onInvite={me?.kind === "account" ? () => setView({ kind: "invite" }) : undefined}
              onMemory={me ? () => setView({ kind: "memory" }) : undefined}
              onVoice={() => setView({ kind: "voice" })} onMood={() => setView({ kind: "mood" })}
              onAnswerTime={() => setView({ kind: "answer" })}
              onReminder={me?.kind === "account" ? () => setView({ kind: "reminderSettings" }) : undefined} />
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
  // PRG-7: after the first talk and its drills offer, the reminder time, once, before "See you tomorrow" (accounts).
  if (loop.askReminder && account) return <ReminderStep firstLoop onDone={reminderDone} />;
  return homePage(turnOn ? <ReminderTurnOn reminder={turnOn} onClose={(r) => {
    closeTurnOn();
    if (r === "denied") setReminderNote(`Come back around ${formatTime(turnOn.time)}`);
  }} /> : undefined);
}
