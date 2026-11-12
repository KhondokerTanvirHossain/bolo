"use client";
import { useEffect, useState, type ReactNode } from "react";
import { unblockGuidance } from "@/audio/micErrors";
import { CoachFlow } from "@/coach/CoachFlow";
import { HelpLink } from "@/help/HelpLink";
import { Icon, Scene } from "@/scene/Scene";
import { TalkChat } from "./TalkChat";
import { TimeLeft } from "./TimeLeft";
import type { TalkState } from "./talkState";

type Props = {
  state: TalkState;
  deviceId?: string | null;
  onDone: () => void;
  onEnd: () => void;
  onTalkAgain: () => void;
  onLeave: () => void;
  onPause: () => void;
  onResume: () => void;
  onRepeat: () => void;
  /** US-2.7 answer to "Keep Bolo slower next time?". */
  onKeepSlower?: (keep: boolean) => void;
  /** SET-2: the learner's captions setting, used when this device has no in-talk choice of its own. */
  captionsDefault?: boolean;
  /** Passed to the Coach after the talk (first-loop actions, finished-talk counting). */
  onCoachReady?: () => void;
  onPractise?: (pattern?: string, talkId?: string) => void;
  /** P3a, Settings → Talk view: the live chat (default) or the classic caption-only screen. */
  talkView?: "chat" | "classic";
  /** P3a: the mic level (0–1) for the meter. */
  level?: number;
};

const LIVE = ["connecting", "buddy_speaking", "your_turn", "thinking"];
const CAPTIONS_KEY = "bolo.captions";
/** US-2.12: a reply slower than this shows "Bolo is thinking…" instead of a frozen screen. */
const THINKING_NOTE_MS = 4000;
/** P3a: the meter's five bars, louder in the middle. */
const BARS = [0.55, 0.8, 1, 0.8, 0.55];

function seconds(ms: number): string {
  return (ms / 1000).toFixed(1);
}

function readCaptions(fallback: boolean): boolean {
  try {
    const v = window.localStorage.getItem(CAPTIONS_KEY);
    return v === null ? fallback : v !== "off";
  } catch {
    return fallback;
  }
}

/**
 * The conversation (TLK-16, changed 2026-11-12), in one of two views (Settings → Talk view):
 * - **Chat (the default):** the talk as a live chat in the sky above the scene, the time left in numbers, and a mic
 *   level meter.
 * - **Classic:** Bolo's caption only (TLK-8 as it was) and the time arc.
 *
 * Both views have Pause, Done and End, the hint card (TLK-6), the paused state (TLK-10) and the crisis card (TLK-14).
 */
export function TalkScreen({ state, deviceId = null, onDone, onEnd, onTalkAgain, onLeave, onPause, onResume, onRepeat, onKeepSlower,
  captionsDefault = true, onCoachReady, onPractise, talkView = "chat", level = 0 }: Props) {
  const [asking, setAsking] = useState(false);
  const [noDebrief, setNoDebrief] = useState(false);
  const [captions, setCaptions] = useState(true);
  const [thinkingLong, setThinkingLong] = useState(false);
  const [startedAt] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  const live = LIVE.includes(state.phase);
  const chatView = talkView === "chat";

  useEffect(() => { setCaptions(readCaptions(captionsDefault)); }, [captionsDefault]);

  useEffect(() => {
    if (!live || chatView) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [live, chatView]);

  useEffect(() => {
    if (!live) setAsking(false);
  }, [live]);

  useEffect(() => {
    setThinkingLong(false);
    if (state.phase !== "thinking") return;
    const t = setTimeout(() => setThinkingLong(true), THINKING_NOTE_MS);
    return () => clearTimeout(t);
  }, [state.phase]);

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

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      // Browser shortcuts (Cmd/Ctrl+R reload, Ctrl+P print…) are the browser's.
      if (!live || e.metaKey || e.ctrlKey || e.altKey) return;
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
      } else if (e.key === "c" || e.key === "C") {
        e.preventDefault();
        toggleCaptions();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [live, asking, state.phase, onDone, onPause, onRepeat]);

  // A talk with at least one turn goes to the Coach's debrief (DBF); an empty one keeps the plain summary.
  if (state.phase === "ended" && state.talkId && (state.summary?.turns ?? 0) > 0 && !noDebrief) {
    return <CoachFlow talkId={state.talkId} deviceId={deviceId} onTalkAgain={onTalkAgain} onDone={onLeave}
      onNoDebrief={() => setNoDebrief(true)} onReady={onCoachReady} onPractise={onPractise} />;
  }
  if (state.phase === "crisis" && state.crisis) {
    return <CrisisCard crisis={state.crisis} onClose={onLeave} />;
  }

  const left = Math.max(0, 1 - (now - startedAt) / (state.minutes * 60_000));
  const yourTurn = state.phase === "your_turn";

  // What the screen says when it isn't showing Bolo's words: the same lines in both views.
  let status: ReactNode = null;
  if (state.phase === "thinking" && thinkingLong) status = <span className="dim">Bolo is thinking…</span>;
  if (state.phase === "connecting") status = <span className="dim">Bolo is getting ready…</span>;
  if (state.phase === "paused") status = "Paused. Tap to continue.";
  if (state.phase === "busy") status = "Bolo is in another talk right now. Try again in a few minutes.";
  if (state.phase === "no_voice") status = "Bolo lost its voice for a moment. Your talk so far is saved.";
  if (state.phase === "no_mic") {
    status = state.micProblem === "busy"
      ? "Another app is using your microphone. Close it, then try again."
      : state.micProblem === "no-device"
        ? "Bolo can't find a microphone. Plug one in, then try again."
        : `Bolo can't hear you yet. ${unblockGuidance(navigator.userAgent)}`;
  }
  if (state.phase === "ended" && state.summary) {
    const n = state.summary.turns;
    status = `Nice talk. You took ${n} ${n === 1 ? "turn" : "turns"}.`;
  }
  const latency = state.phase === "ended" && state.summary?.p50Ms != null && state.summary.p90Ms != null && (
    <p className="bo-footnote" style={{ color: "var(--on-scene-secondary)", marginTop: 12 }}>
      Bolo answered in about {seconds(state.summary.p50Ms)} s (slowest {seconds(state.summary.p90Ms)} s).
    </p>
  );

  const topRight = (
    <span style={{ display: "flex", gap: 8 }}>
      {(live || state.phase === "paused") && (
        <button className="bo-scenebtn" aria-label="Captions" aria-pressed={captions} onClick={toggleCaptions}>
          <Icon name={captions ? "closed_caption" : "closed_caption_disabled"} size="sm" />
        </button>
      )}
      {(live || state.phase === "paused") && <button className="bo-scenebtn" onClick={() => setAsking(true)}>End</button>}
    </span>
  );

  const overlays = (
    <>
      {live && state.askKeepSlower && onKeepSlower && (
        <div className="bo-hint" role="note" aria-label="Keep Bolo slower?"
          style={{ position: "absolute", left: "50%", top: 72, transform: "translateX(-50%)", width: "min(420px, calc(100% - 32px))", zIndex: 3 }}>
          <p className="bo-hint__label">Keep Bolo slower next time?</p>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button className="bo-btn bo-btn--secondary" onClick={() => onKeepSlower(true)}>Yes, keep it</button>
            <button className="bo-btn bo-btn--quiet-neutral" onClick={() => onKeepSlower(false)}>Not now</button>
          </div>
        </div>
      )}
      {yourTurn && state.hint && (
        <div className="bo-hint" role="note"
          style={{ position: "absolute", left: "50%", bottom: 150, transform: "translateX(-50%)", width: "min(520px, calc(100% - 32px))", zIndex: 2 }}>
          <p className="bo-hint__label"><Icon name="lightbulb" size="sm" />Try starting with</p>
          {state.hint.map((s) => <p className="bo-hint__starter" key={s}>{s}</p>)}
        </div>
      )}
    </>
  );

  const dock = (
    <div className="dk-dock">
      {live ? (
        <>
          <button className="bo-scenebtn" aria-label="Pause" disabled={state.phase === "connecting"} onClick={onPause}>
            <Icon name="pause" fill />
          </button>
          {chatView && (
            <div className={`bo-level${yourTurn ? "" : " bo-level--rest"}`} aria-hidden="true">
              {BARS.map((w, i) => <i key={i} style={{ "--l": yourTurn ? Math.max(0.15, level * w) : 0.15 } as React.CSSProperties} />)}
            </div>
          )}
          <div className={`bo-mic ${yourTurn ? "bo-mic--turn" : "bo-mic--thinking"}${chatView ? " bo-mic--noarc" : ""}`} role="status"
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
          <HelpLink ctx={{ view: "talk", talkId: state.talkId ?? undefined }} className="bo-scenebtn" />
        </>
      )}
    </div>
  );

  const keys = live && (
    <div className="dk-keys">
      <span><span className="dk-kbd">Space</span>Done talking</span>
      <span><span className="dk-kbd">P</span>Pause</span>
      <span><span className="dk-kbd">R</span>Repeat</span>
      <span><span className="dk-kbd">Esc</span>End</span>
      {chatView && <span><span className="dk-kbd">C</span>Chat on/off</span>}
    </div>
  );

  const endDialog = asking && (
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
  );

  if (chatView) {
    return (
      <section className="dk-screen on" data-screen="talk" data-view="chat">
        <Scene />
        <div className="dk-talk__top">
          <span />
          <TimeLeft clock={state.clock} minutes={state.minutes} />
          {topRight}
        </div>
        <div className="dk-talk__chat">
          {captions ? (
            <TalkChat chat={state.chat} live={state.live} replay={state.replay} canRepeat={yourTurn} onRepeat={onRepeat}
              status={status ? <>{status}{latency}</> : null} />
          ) : (status && <div className="bo-chat"><p className="bo-chat__status" role="status">{status}{latency}</p></div>)}
        </div>
        {overlays}
        {dock}
        {keys}
        {endDialog}
      </section>
    );
  }

  const caption: ReactNode = status ?? (captions ? (state.caption || " ") : " ");
  return (
    <section className="dk-screen on" data-screen="talk">
      <Scene />
      <div className="dk-talk__top">
        <span />
        <span style={{ color: "var(--on-scene-secondary)", font: "500 14px/20px var(--font-sans)" }}>
          Free talk · {state.minutes} min
        </span>
        {topRight}
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
        {latency}
      </div>
      {overlays}
      {dock}
      {keys}
      {endDialog}
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
