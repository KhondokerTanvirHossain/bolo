"use client";
import { useEffect, useState } from "react";
import { unblockGuidance } from "@/audio/micErrors";
import { Icon, Scene } from "@/scene/Scene";
import type { TalkState } from "./talkState";

type Props = { state: TalkState; onDone: () => void; onEnd: () => void; onTalkAgain: () => void; onLeave: () => void };

const LIVE = ["connecting", "buddy_speaking", "your_turn", "thinking"];

function seconds(ms: number): string {
  return (ms / 1000).toFixed(1);
}

/** The conversation (TLK-16): the scene, Bolo's caption, the mic state, the time left, Done and End. Nothing else. */
export function TalkScreen({ state, onDone, onEnd, onTalkAgain, onLeave }: Props) {
  const [asking, setAsking] = useState(false);
  const [startedAt] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  const live = LIVE.includes(state.phase);

  useEffect(() => {
    if (!live) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [live]);

  useEffect(() => {
    if (!live) setAsking(false);
  }, [live]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!live) return;
      if (e.key === "Escape") {
        e.preventDefault();
        setAsking((a) => !a);
      } else if ((e.key === " " || e.code === "Space") && !asking && state.phase === "your_turn") {
        e.preventDefault();
        onDone();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [live, asking, state.phase, onDone]);

  const left = Math.max(0, 1 - (now - startedAt) / (state.minutes * 60_000));
  const yourTurn = state.phase === "your_turn";

  let caption: React.ReactNode = state.caption || " ";
  if (state.phase === "connecting") caption = <span className="dim">Bolo is getting ready…</span>;
  if (state.phase === "busy") caption = "Bolo is in another talk right now. Try again in a few minutes.";
  if (state.phase === "no_voice") caption = "Bolo lost its voice for a moment. Your talk so far is saved.";
  if (state.phase === "no_mic") {
    caption = state.micProblem === "busy"
      ? "Another app is using your microphone. Close it, then try again."
      : state.micProblem === "no-device"
        ? "Bolo can't find a microphone. Plug one in, then try again."
        : `Bolo can't hear you yet. ${unblockGuidance(navigator.userAgent)}`;
  }
  if (state.phase === "ended" && state.summary) {
    const n = state.summary.turns;
    caption = `Nice talk. You took ${n} ${n === 1 ? "turn" : "turns"}.`;
  }

  return (
    <section className="dk-screen on" data-screen="talk">
      <Scene />
        <div className="dk-talk__top">
          <span />
          <span style={{ color: "var(--on-scene-secondary)", font: "500 14px/20px var(--font-sans)" }}>
            Free talk · {state.minutes} min
          </span>
          {live ? <button className="bo-scenebtn" onClick={() => setAsking(true)}>End</button> : <span />}
        </div>
        <div className="dk-talk__cap">
          <div className="bo-caption" aria-live="polite">
            <p className="bo-caption__text">{caption}</p>
          </div>
          {state.phase === "ended" && state.summary?.p50Ms != null && state.summary.p90Ms != null && (
            <p className="bo-footnote" style={{ color: "var(--on-scene-secondary)", marginTop: 12 }}>
              Bolo answered in about {seconds(state.summary.p50Ms)} s (slowest {seconds(state.summary.p90Ms)} s).
            </p>
          )}
        </div>
        <div className="dk-dock">
          {live ? (
            <>
              <div className={`bo-mic ${yourTurn ? "bo-mic--turn" : "bo-mic--thinking"}`} role="status"
                aria-label={yourTurn ? "Your turn" : state.phase === "buddy_speaking" ? "Bolo is talking" : "Bolo is thinking"}>
                <svg className="arc" viewBox="0 0 100 100" aria-hidden="true" style={{ pointerEvents: "none" }}>
                  <circle className="track" cx="50" cy="50" r="48" pathLength={100} />
                  <circle className="left" cx="50" cy="50" r="48" pathLength={100} strokeDasharray={`${left * 100} 100`} />
                </svg>
                <div className="bo-mic__core"><Icon name="mic" size="lg" /></div>
              </div>
              <button className="bo-scenebtn" aria-label="Done, end my turn" disabled={!yourTurn} onClick={onDone}>Done</button>
            </>
          ) : state.phase === "ended" ? (
            <>
              <button className="bo-btn bo-btn--primary" onClick={onTalkAgain}>Talk again</button>
              <button className="bo-scenebtn" onClick={onLeave}>Done</button>
            </>
          ) : (
            <>
              <button className="bo-btn bo-btn--primary" onClick={onTalkAgain}>Try again</button>
              <button className="bo-scenebtn" onClick={onLeave}>Back</button>
            </>
          )}
        </div>
        {live && (
          <div className="dk-keys">
            <span><span className="dk-kbd">Space</span>Done talking</span>
            <span><span className="dk-kbd">Esc</span>End</span>
          </div>
        )}
      {asking && (
        <>
          <div className="dk-dim" />
          <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="end-title">
            <h2 className="bo-sheet__title" id="end-title">End this talk?</h2>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 16 }}>
              <button className="bo-btn bo-btn--secondary" onClick={() => { setAsking(false); onEnd(); }}>End</button>
              <button className="bo-btn bo-btn--primary" onClick={() => setAsking(false)} autoFocus>Keep talking</button>
            </div>
            <p className="bo-footnote" style={{ marginTop: 12 }}><span className="dk-kbd">Esc</span> keeps talking</p>
          </div>
        </>
      )}
    </section>
  );
}
