"use client";
import { myPayment, paymentLine, type MyPayment } from "@/plan/payments";
import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchHome } from "@/home/home";
import { counterLine, planName, type FreeTalks } from "@/plan/freeTalks";
import type { LearnerView } from "@/api/browser";
import { GoogleButton } from "@/account/GoogleButton";
import { signInWithGoogle, signOut } from "@/account/session";
import { signInProblem } from "@/account/signInCopy";
import { QUESTIONS } from "@/onboarding/questions";
import { Icon } from "@/scene/Scene";
import { SpeakingSettings } from "./SpeakingSettings";
import { HelpDialog } from "@/help/HelpDialog";
import { fetchMemory, meRowLine, type MemoryView } from "@/memory/memory";

const LABEL: Record<string, string> = Object.fromEntries(QUESTIONS.flatMap((q) => q.chips.map((c) => [c.value, c.label] as const)));

/** ScreenMe: who you are, your goals, Speaking, Privacy, and sign in or out. Nothing nested more than 2 levels. */
export function MeScreen({ me, onPrivacy, onSignedIn, onInvite, onMemory, onVoice, onMood, onAnswerTime }: { me: LearnerView | null; onPrivacy: () => void; onSignedIn: (l: LearnerView) => void; onInvite?: () => void; onMemory?: () => void; onVoice?: () => void; onMood?: () => void; onAnswerTime?: () => void }) {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const account = me?.account ?? null;
  const [help, setHelp] = useState(false);
  const [free, setFree] = useState<FreeTalks | null>(null);
  useEffect(() => { if (account) void fetchHome().then((h) => setFree(h?.freeTalks ?? null)); }, [account]);
  /** P2e: a bKash payment being checked, or why it wasn't confirmed. */
  const [payment, setPayment] = useState<MyPayment | null>(null);
  useEffect(() => { if (account) void myPayment().then(setPayment); }, [account]);
  const paymentStatus = paymentLine(payment);
  /** P3b: the "What Bolo remembers" row's sub-line. */
  const [memory, setMemory] = useState<MemoryView | null>(null);
  const memoryRow = Boolean(onMemory);
  // Keyed on whether the row shows, not on the callback (a new function each render would refetch forever).
  useEffect(() => { if (memoryRow) void fetchMemory().then(setMemory); }, [memoryRow]);
  const google = async () => {
    setBusy(true);
    setProblem(null);
    const r = await signInWithGoogle();
    setBusy(false);
    if (r.kind === "ok") onSignedIn(r.learner);
    else setProblem(signInProblem(r));
  };
  return (
    <>
      <h1 className="dk-h1">Me</h1>
      <p className="dk-sub">{account ? (free ? `${account.email} · ${planName(free) ?? "Free"} plan` : account.email) : "Guest · your progress is in this browser only"}</p>
      {!account && me && (
        <div className="bo-group" style={{ marginTop: 20, padding: 20, maxWidth: 420 }}>
          <p className="bo-row__title" style={{ margin: "0 0 12px" }}>Save your progress</p>
          <GoogleButton onClick={() => void google()} busy={busy} />
          {problem && <p className="bo-footnote" role="alert" style={{ marginTop: 10 }}>{problem}</p>}
        </div>
      )}
      {free && (
        <>
          <h2 className="dk-h2" style={{ marginTop: 28 }}>Plan</h2>
          <div className="bo-group">
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">{planName(free) ?? "Free"} plan</div>
              <div className="bo-row__sub">{counterLine(free)}</div>
              {paymentStatus && <div className="bo-row__sub" role="status">{paymentStatus}</div>}</div>
              <Link className="dk-link" href="/pricing">See plans</Link></div>
          </div>
        </>
      )}
      <h2 className="dk-h2" style={{ marginTop: 28 }}>My goals</h2>
      <div className="bo-group">
        {me?.profile ? (
          <>
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">{LABEL[me.profile.goal] ?? me.profile.goal}</div><div className="bo-row__sub">What you need English for</div></div></div>
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">{LABEL[me.profile.fearedMoment] ?? me.profile.fearedMoment}</div><div className="bo-row__sub">The hardest moment</div></div></div>
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">{free ? 5 : me.profile.talkMinutes} minutes</div><div className="bo-row__sub">{free ? "Each talk on your plan" : "Each talk"}</div></div></div>
          </>
        ) : <div className="bo-row"><div className="bo-row__body"><div className="bo-row__sub">No goals yet.</div></div></div>}
      </div>
      <h2 className="dk-h2" style={{ marginTop: 28 }}>Speaking</h2>
      <SpeakingSettings onVoice={onVoice} onMood={onMood} onAnswerTime={onAnswerTime} />
      <h2 className="dk-h2" style={{ marginTop: 28 }}>Privacy</h2>
      <div className="bo-group">
        {onMemory && (
          <button className="bo-row" style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }} onClick={onMemory}>
            <Icon name="auto_stories" />
            <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>What Bolo remembers</span>
              <span className="bo-row__sub">{meRowLine(memory)}</span></span>
            <Icon name="chevron_right" />
          </button>
        )}
        <button className="bo-row" style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }} onClick={onPrivacy}>
          <Icon name="shield_person" />
          <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>Privacy</span>
            <span className="bo-row__sub">What Bolo keeps, your recordings, delete everything</span></span>
          <Icon name="chevron_right" />
        </button>
        <button className="bo-row" style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }} onClick={() => setHelp(true)}>
          <Icon name="help" />
          <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>Help</span>
            <span className="bo-row__sub">Stuck? Report a problem</span></span>
          <Icon name="chevron_right" />
        </button>
        {onInvite && (
          <button className="bo-row" style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }} onClick={onInvite}>
            <Icon name="group_add" />
            <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>Invite friends</span>
              <span className="bo-row__sub">3 friends who talk give you a month of Premium</span></span>
            <Icon name="chevron_right" />
          </button>
        )}
      </div>
      <p className="bo-footnote" style={{ marginTop: 12 }}><a className="dk-link" href="/terms">Terms of Use</a> · <a className="dk-link" href="/refund">Refund policy</a> · <a className="dk-link" href="/privacy">Privacy</a></p>
      {help && <HelpDialog ctx={{ view: "me" }} onClose={() => setHelp(false)} />}
      {account && (
        <div style={{ marginTop: 28 }}>
          <button className="bo-btn bo-btn--secondary" onClick={() => void signOut().then(() => window.location.assign("/"))}>Sign out</button>
        </div>
      )}
    </>
  );
}
