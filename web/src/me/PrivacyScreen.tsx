"use client";
import { useState } from "react";
import { api, POLICY_VERSION } from "@/api/browser";
import { DeleteEverything } from "./DeleteEverything";

/** US-8.5: the keep choice, saved as a new consent record; it applies from the next talk. */
export function KeepRecordingsRow({ keep }: { keep: boolean }) {
  const [on, setOn] = useState(keep);
  const [state, setState] = useState<"idle" | "saved" | "failed">("idle");
  const flip = async () => {
    const next = !on;
    setOn(next);
    const r = await api.post<void>("/api/consent", { keepRecordings: next, over13: true, policyVersion: POLICY_VERSION });
    if (r.kind === "ok") setState("saved");
    else {
      setOn(!next);
      setState("failed");
    }
  };
  return (
    <div className="bo-row">
      <div className="bo-row__body">
        <div className="bo-row__title" id="keep-title">Keep my recordings</div>
        <div className="bo-row__sub">
          {state === "failed" ? "That didn't save. Try again." : state === "saved" ? "Saved. This applies from your next talk."
            : on ? "Clips are kept for 90 days, so you can hear your progress." : "Your audio is deleted within 24 hours. Text and scores stay."}
        </div>
      </div>
      <button className="bo-toggle" role="switch" aria-checked={on} aria-labelledby="keep-title" onClick={() => void flip()} />
    </div>
  );
}

/** Me → Privacy (ScreenPrivacy): what is kept, why and for how long, then the controls. */
export function PrivacyScreen({ keep, onDeleted }: { keep: boolean; onDeleted: () => void }) {
  const [asking, setAsking] = useState(false);
  return (
    <>
      <h1 className="dk-h1">Privacy</h1>
      <p className="dk-sub">What Bolo keeps, why, and for how long.</p>
      <div className="bo-group" style={{ marginTop: 24, padding: "14px 20px" }}>
        <p className="bo-row__title" style={{ margin: 0 }}>What Bolo keeps</p>
        <ul className="bo-row__sub" style={{ margin: "6px 0 0", paddingLeft: 18, fontSize: 15, lineHeight: "22px" }}>
          <li>Your voice clips, only if you choose to keep them (90 days). Your Day 1 clip stays until you delete everything, so you can hear your progress.</li>
          <li>The text of your talks and your scores, for feedback and progress.</li>
          <li>Your Google email and name, when you sign in.</li>
        </ul>
        <p className="bo-row__sub" style={{ margin: "10px 0 0", fontSize: 15 }}>
          Bolo never sells your data. <a className="dk-link" href="/privacy" target="_blank" rel="noreferrer">Read the full privacy page</a>
        </p>
      </div>
      {/* Keyed by the server's value: when fresher data arrives, the switch shows it rather than a stale first read. */}
      <div className="bo-group" style={{ marginTop: 16 }}><KeepRecordingsRow key={String(keep)} keep={keep} /></div>
      <div style={{ marginTop: 28 }}>
        <button className="bo-btn bo-btn--critical" onClick={() => setAsking(true)}>Delete everything</button>
        <p className="bo-row__sub" style={{ margin: "4px 0 0" }}>Your account and all your data, at once.</p>
      </div>
      {asking && <DeleteEverything onCancel={() => setAsking(false)} onDeleted={onDeleted} />}
    </>
  );
}
