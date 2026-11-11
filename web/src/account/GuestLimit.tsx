"use client";
import { useRef, useState } from "react";
import { useDialog } from "@/ui/useDialog";
import type { LearnerView } from "@/api/browser";
import { GoogleButton } from "./GoogleButton";
import { KeepList } from "./KeepList";
import { signInWithGoogle } from "./session";
import { signInProblem } from "./signInCopy";

/**
 * Sign in before the first talk (P1d, replacing ACC-2's 3 guest talks): free talks belong to a Google account. Built on
 * ScreenGuestLimit: nothing set up so far is lost. Not now leaves Home as it was.
 */
export function GuestLimit({ me, onSignedIn, onClose }: { me: LearnerView | null; onSignedIn: (l: LearnerView) => void; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const kept = me?.consent?.keepRecordings !== false;
  const box = useRef<HTMLDivElement>(null);
  useDialog(box, () => { if (!busy) onClose(); });
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
      <div className="dk-card" ref={box} role="dialog" aria-modal="true" aria-labelledby="limit-title">
        <h1 className="bo-flow__title" id="limit-title">Sign in to start talking</h1>
        <p className="bo-flow__body">Bolo is free with your Google account: 7 talks in your first week, then 2 every month. Your answers{kept ? " and your Day 1 clip" : ""} come with you.</p>
        <KeepList rows={[
          { icon: "flag", title: "Your answers", sub: "What you need English for, and the hardest moment", tone: "coach" },
          { icon: "mic", title: "Your Day 1 clip", sub: kept ? "Kept, as you chose at the start" : "Stays on this device, as you chose", tone: "voice" },
        ]} />
        <div style={{ display: "grid", gap: 10, marginTop: 20 }}>
          <GoogleButton onClick={() => void google()} busy={busy} />
          <button className="bo-btn bo-btn--quiet-neutral bo-btn--block" onClick={onClose} disabled={busy}>Not now</button>
        </div>
        {problem && <p className="bo-footnote" role="alert" style={{ marginTop: 12 }}>{problem}</p>}
        <p className="bo-footnote" style={{ marginTop: 12 }}>By continuing you agree to the <a className="dk-link" href="/terms" target="_blank" rel="noreferrer">Terms</a> and the <a className="dk-link" href="/privacy" target="_blank" rel="noreferrer">Privacy page</a>.</p>
      </div>
    </>
  );
}
