"use client";
import { useEffect, useState } from "react";
import { unblockGuidance } from "@/audio/micErrors";
import { CoachFlow } from "@/coach/CoachFlow";
import { Icon, Scene } from "@/scene/Scene";
import type { TalkState } from "./talkState";

type Props = {
  state: TalkState;
  onDone: () => void;
  onEnd: () => void;
  onTalkAgain: () => void;
  onLeave: () => void;
  onPause: () => void;
  onResume: () => void;
  onRepeat: () => void;
};

const LIVE = ["connecting", "buddy_speaking", "your_turn", "thinking"];
const CAPTIONS_KEY = "bolo.captions";
/** US-2.12: a reply slower than this shows "Bolo is thinking…" instead of a frozen screen. */
const THINKING_NOTE_MS = 4000;

function seconds(ms: number): string {
  return (ms / 1000).toFixed(1);
}

function readCaptions(): boolean {
  try {
    return window.localStorage.getItem(CAPTIONS_KEY) !== "off";
  } catch {
    return true;
  }
}

/**
 * The conversation (TLK-16): the scene, Bolo's caption (TLK-8, toggle), the mic state, the time left, Pause, Done and
 * End; plus the hint card (TLK-6), the paused state (TLK-10) and the crisis card (TLK-14).
 */
export function TalkScreen({ state, onDone, onEnd, onTalkAgain, onLeave, onPause, onResume, onRepeat }: Props) {
  const [asking, setAsking] = useState(false);
  const [noDebrief, setNoDebrief] = useState(false);
  const [captions, setCaptions] = useState(true);
  const [thinkingLong, setThinkingLong] = useState(false);
  const [startedAt] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  const live = LIVE.includes(state.phase);

  useEffect(() => { setCaptions(readCaptions()); }, []);

  useEffect(() => {
    if (!live) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [live]);

  useEffect(() => {
    if (!live) setAsking(false);
  }, [live]);

  useEffect(() => {
    setThinkingLong(false);
    if (state.phase !== "thinking") return;
    const t = setTimeout(() => setThinkingLong(true), THINKING_NOTE_MS);
    return () => clearTimeout(t);
  }, [state.phase]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!live) return;
      if (e.key === "Escape") {
        e.preventDefault();
        setAsking((a) => !a);
      } else if (asking) {
        return;
      } else if ((e.key === " " || e.code === "Space") && state.phase === "your_turn") {
        e.preventDefault();
        onDone();
      } else if ((e.key === "p" || e.key === "P") && state.phase !== "connecting") {
        e.preventDefault();
        onPause();
      } else if ((e.key === "r" || e.key === "R") && state.phase === "your_turn") {
        e.preventDefault();
        onRepeat();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [live, asking, state.phase, onDone, onPause, onRepeat]);

  const toggleCaptions = () => {
    setCaptions((c) => {
      try {
        window.localStorage.setItem(CAPTIONS_KEY, c ? "off" : "on");
      } catch {
        // per-viewer convenience only
      }
      return !c;
    });
  };

  // A talk with at least one turn goes to the Coach's debrief (DBF); an empty one keeps the plain summary.
  if (state.phase === "ended" && state.talkId && (state.summary?.turns ?? 0) > 0 && !noDebrief) {
    return <CoachFlow talkId={state.talkId} onTalkAgain={onTalkAgain} onDone={onLeave} onNoDebrief={() => setNoDebrief(true)} />;
  }
  if (state.phase === "crisis" && state.crisis) {
    return <CrisisCard crisis={state.crisis} onClose={onLeave} />;
  }

  const left = Math.max(0, 1 - (now - startedAt) / (state.minutes * 60_000));
  const yourTurn = state.phase === "your_turn";

  let caption: React.ReactNode = captions ? (state.caption || " ") : " ";
  if (state.phase === "thinking" && thinkingLong) caption = <span className="dim">Bolo is thinking…</span>;
  if (state.phase === "connecting") caption = <span className="dim">Bolo is getting ready…</span>;
  if (state.phase === "paused") caption = "Paused. Tap to continue.";
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
        <span style={{ display: "flex", gap: 8 }}>
          {(live || state.phase === "paused") && (
            <button className="bo-scenebtn" aria-label="Captions" aria-pressed={captions} onClick={toggleCaptions}>
              <Icon name={captions ? "closed_caption" : "closed_caption_disabled"} size="sm" />
            </button>
          )}
          {(live || state.phase === "paused") && <button className="bo-scenebtn" onClick={() => setAsking(true)}>End</button>}
        </span>
      </div>
      <div className="dk-talk__cap">
        <div className="bo-caption" aria-live="polite">
          <p className="bo-caption__text">{caption}</p>
          {yourTurn && captions && state.caption && (
            <button className="bo-caption__replay" aria-label="Repeat that" onClick={onRepeat}>
              <Icon name="replay" size="sm" />
            </button>
          )}
        </div>
        {state.phase === "ended" && state.summary?.p50Ms != null && state.summary.p90Ms != null && (
          <p className="bo-footnote" style={{ color: "var(--on-scene-secondary)", marginTop: 12 }}>
            Bolo answered in about {seconds(state.summary.p50Ms)} s (slowest {seconds(state.summary.p90Ms)} s).
          </p>
        )}
      </div>
      {yourTurn && state.hint && (
        <div className="bo-hint" role="note"
          style={{ position: "absolute", left: "50%", bottom: 150, transform: "translateX(-50%)", width: "min(520px, calc(100% - 32px))", zIndex: 2 }}>
          <p className="bo-hint__label"><Icon name="lightbulb" size="sm" />Try starting with</p>
          {state.hint.map((s) => <p className="bo-hint__starter" key={s}>{s}</p>)}
        </div>
      )}
      <div className="dk-dock">
        {live ? (
          <>
            <button className="bo-scenebtn" aria-label="Pause" disabled={state.phase === "connecting"} onClick={onPause}>
              <Icon name="pause" fill />
            </button>
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
        ) : state.phase === "paused" ? (
          <>
            <button className="bo-btn bo-btn--primary" onClick={onResume} autoFocus><Icon name="play_arrow" fill />Continue</button>
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
          <span><span className="dk-kbd">P</span>Pause</span>
          <span><span className="dk-kbd">R</span>Repeat</span>
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

/** TLK-14: the approved crisis content, with tappable local helplines. No practice, no debrief. */
function CrisisCard({ crisis, onClose }: { crisis: NonNullable<TalkState["crisis"]>; onClose: () => void }) {
  return (
    <section className="dk-screen on" data-screen="talk">
      <Scene />
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="crisis-title">
        <h1 className="bo-flow__title" id="crisis-title">{crisis.title}</h1>
        {crisis.lines.map((l) => <p className="bo-flow__body" key={l} style={{ margin: "8px 0 0" }}>{l}</p>)}
        <ul style={{ listStyle: "none", padding: 0, margin: "16px 0 0", display: "grid", gap: 10 }}>
          {crisis.help.map((h) => (
            <li key={h.number}>
              <a className="bo-btn bo-btn--secondary bo-btn--block" href={`tel:${h.number.replace(/[^0-9+]/g, "")}`}>
                <Icon name="call" fill />{h.name}: {h.number}
              </a>
              <p className="bo-footnote" style={{ margin: "4px 0 0", textAlign: "center" }}>{h.hours}</p>
            </li>
          ))}
        </ul>
        <button className="bo-btn bo-btn--quiet-neutral bo-btn--block" style={{ marginTop: 16 }} onClick={onClose}>Close</button>
      </div>
    </section>
  );
}
