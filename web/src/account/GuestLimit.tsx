"use client";
import { useState } from "react";
import type { LearnerView } from "@/api/browser";
import { GoogleButton } from "./GoogleButton";
import { KeepList } from "./KeepList";
import { signInWithGoogle } from "./session";
import { signInProblem } from "./signInCopy";

/** ScreenGuestLimit (ACC-2, US-8.2): the 4th guest talk asks for sign-in; nothing is lost. Not now leaves Home as it was. */
export function GuestLimit({ me, onSignedIn, onClose }: { me: LearnerView | null; onSignedIn: (l: LearnerView) => void; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const kept = me?.consent?.keepRecordings !== false;
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
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="limit-title">
        <h1 className="bo-flow__title" id="limit-title">Save your progress to keep talking</h1>
        <p className="bo-flow__body">You&apos;ve had 3 talks as a guest. Sign in and everything so far comes with you.</p>
        <KeepList rows={[
          { icon: "history", title: "Your talks and their feedback", sub: "Scored and saved", tone: "coach" },
          { icon: "mic", title: "Your Day 1 clip", sub: kept ? "Kept, as you chose at the start" : "Your talks were scored; recordings weren't kept", tone: "voice" },
        ]} />
        <div style={{ display: "grid", gap: 10, marginTop: 20 }}>
          <GoogleButton onClick={() => void google()} busy={busy} />
          <button className="bo-btn bo-btn--quiet-neutral bo-btn--block" onClick={onClose} disabled={busy}>Not now</button>
        </div>
        {problem && <p className="bo-footnote" role="alert" style={{ marginTop: 12 }}>{problem}</p>}
      </div>
    </>
  );
}
