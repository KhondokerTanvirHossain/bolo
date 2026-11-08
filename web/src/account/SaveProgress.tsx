"use client";
import { useState } from "react";
import type { LearnerView } from "@/api/browser";
import { GOAL_LABEL } from "./labels";
import { GoogleButton } from "./GoogleButton";
import { KeepList, type KeepRow } from "./KeepList";
import { signInWithGoogle } from "./session";
import { signInProblem } from "./signInCopy";

type Props = { me: LearnerView | null; hasDay1: boolean; onSignedIn: (learner: LearnerView) => void; onStayGuest: () => void };

/**
 * Save your progress (ScreenSaveProgress, US-8.1) and, after Not now, "Stay a guest for now?" (ScreenGuestNotNow).
 * Screens with Google's button have no mustard button; "Save my progress" leads only on the guest step (design).
 */
export function SaveProgress({ me, hasDay1, onSignedIn, onStayGuest }: Props) {
  const [stage, setStage] = useState<"save" | "notnow">("save");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  const google = async () => {
    setBusy(true);
    setProblem(null);
    const r = await signInWithGoogle();
    setBusy(false);
    if (r.kind === "ok") onSignedIn(r.learner);
    else setProblem(signInProblem(r));
  };

  const rows: KeepRow[] = [];
  if (hasDay1) rows.push({ icon: "mic", title: "Your Day 1 clip", sub: "We'll play it back on Day 7", tone: "voice" });
  rows.push({ icon: "forum", title: "Today's feedback", sub: "Your coach's strength and things to try", tone: "coach" });
  if (me?.profile) rows.push({ icon: "flag", title: "Your goals", sub: GOAL_LABEL[me.profile.goal] ?? "Your setup answers", tone: "buddy" });

  return (
    <section className="dk-screen on" data-screen="save">
      <div className="dk-dim" />
      {stage === "save" ? (
        <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="save-title">
          <h1 className="bo-flow__title" id="save-title">Save your progress</h1>
          <p className="bo-flow__body">Keep your Day 1 voice and today&apos;s feedback, on this computer and your phone.</p>
          <KeepList rows={rows} />
          <div style={{ display: "grid", gap: 10, marginTop: 20 }}>
            <GoogleButton onClick={() => void google()} busy={busy} />
            <button className="bo-btn bo-btn--quiet-neutral bo-btn--block" onClick={() => setStage("notnow")} disabled={busy}>Not now</button>
          </div>
          {problem && <p className="bo-footnote" role="alert" style={{ marginTop: 12 }}>{problem}</p>}
        </div>
      ) : (
        <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="notnow-title">
          <h1 className="bo-flow__title" id="notnow-title">Stay a guest for now?</h1>
          <p className="bo-flow__body">Your progress stays in this browser only. If you clear your browsing data, it goes too.</p>
          <div style={{ display: "grid", gap: 10, marginTop: 20 }}>
            <button className="bo-btn bo-btn--primary bo-btn--block" onClick={() => setStage("save")} autoFocus>Save my progress</button>
            <button className="bo-btn bo-btn--secondary bo-btn--block" onClick={onStayGuest}>Stay a guest</button>
          </div>
        </div>
      )}
    </section>
  );
}
