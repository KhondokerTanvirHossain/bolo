"use client";
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

const LABEL: Record<string, string> = Object.fromEntries(QUESTIONS.flatMap((q) => q.chips.map((c) => [c.value, c.label] as const)));

/** ScreenMe: who you are, your goals, Speaking, Privacy, and sign in or out. Nothing nested more than 2 levels. */
export function MeScreen({ me, onPrivacy, onSignedIn }: { me: LearnerView | null; onPrivacy: () => void; onSignedIn: (l: LearnerView) => void }) {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const account = me?.account ?? null;
  const [free, setFree] = useState<FreeTalks | null>(null);
  useEffect(() => { if (account) void fetchHome().then((h) => setFree(h?.freeTalks ?? null)); }, [account]);
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
              <div className="bo-row__sub">{counterLine(free)}</div></div></div>
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
      <SpeakingSettings />
      <h2 className="dk-h2" style={{ marginTop: 28 }}>Privacy</h2>
      <div className="bo-group">
        <button className="bo-row" style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }} onClick={onPrivacy}>
          <Icon name="shield_person" />
          <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>Privacy</span>
            <span className="bo-row__sub">What Bolo keeps, your recordings, delete everything</span></span>
          <Icon name="chevron_right" />
        </button>
      </div>
      {account && (
        <div style={{ marginTop: 28 }}>
          <button className="bo-btn bo-btn--secondary" onClick={() => void signOut().then(() => window.location.assign("/"))}>Sign out</button>
        </div>
      )}
    </>
  );
}
