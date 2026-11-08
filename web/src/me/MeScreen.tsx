"use client";
import { useState } from "react";
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
      <p className="dk-sub">{account ? `${account.email} · free during the closed beta` : "Guest · your progress is in this browser only"}</p>
      {!account && me && (
        <div className="bo-group" style={{ marginTop: 20, padding: 20, maxWidth: 420 }}>
          <p className="bo-row__title" style={{ margin: "0 0 12px" }}>Save your progress</p>
          <GoogleButton onClick={() => void google()} busy={busy} />
          {problem && <p className="bo-footnote" role="alert" style={{ marginTop: 10 }}>{problem}</p>}
        </div>
      )}
      <h2 className="dk-h2" style={{ marginTop: 28 }}>My goals</h2>
      <div className="bo-group">
        {me?.profile ? (
          <>
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">{LABEL[me.profile.goal] ?? me.profile.goal}</div><div className="bo-row__sub">What you need English for</div></div></div>
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">{LABEL[me.profile.fearedMoment] ?? me.profile.fearedMoment}</div><div className="bo-row__sub">The hardest moment</div></div></div>
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">{me.profile.talkMinutes} minutes</div><div className="bo-row__sub">Each talk</div></div></div>
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
