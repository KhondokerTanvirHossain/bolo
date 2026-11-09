"use client";
import { useEffect } from "react";
import { Icon, Scene } from "@/scene/Scene";
import type { Home } from "./home";
import { boloLine, greeting } from "./greeting";
import { WeekCard } from "./WeekCard";

type Props = {
  home: Home | null;
  name?: string | null;
  /** Talk length shown on the scenario line (5 on the free plan). */
  minutes: number;
  /** The free-plan line; on phones it shows under Talk (the sidebar's card is hidden there). */
  freeLine?: string | null;
  /** A one-off line in place of Bolo's usual one ("See you tomorrow" after the first loop). */
  note?: string;
  hasDay1: boolean;
  /** Space starts a talk only while nothing sits over Home (a sheet turns it off). */
  keysOn: boolean;
  onTalk: () => void;
  onFeedback: (talkId: string) => void;
  onPractice: (pattern?: string) => void;
  onProgress: () => void;
  onPlayDay1: () => void;
};

/**
 * The dashboard Home (DesktopHome, HOM-1, US-6.9): a greeting, Bolo's line in the scene with the Talk disc (the one
 * primary, R1, one tap away, R2), Next up (at most one card by the priority table), and This week (PRG-4).
 */
export function HomeDashboard({ home, name, minutes, freeLine, note, hasDay1, keysOn, onTalk, onFeedback, onPractice, onProgress, onPlayDay1 }: Props) {
  useEffect(() => {
    if (!keysOn) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== " " || e.repeat) return;
      // A focused control keeps its own Space (a card, a link, a text field): only the page's Space starts a talk.
      const t = e.target;
      if (t instanceof Element && t.closest("input, textarea, select, button, a, [contenteditable='true'], [role='dialog']")) return;
      e.preventDefault();
      onTalk();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [keysOn, onTalk]);

  const card = home?.card ?? null;
  return (
    <>
      <p className="dk-sub" style={{ margin: 0 }}>{greeting(new Date().getHours(), name)}</p>
      <h1 className="dk-h1">Ready when you are.</h1>
      <div className="dk-hero">
        <Scene />
        <div className="bo-caption" aria-live="polite">
          <p className="bo-caption__text dk-hero__line">{boloLine(home?.lastTopic, note)}</p>
        </div>
        <div className="dk-hero__hint"><span className="dk-kbd">Space</span>to start talking</div>
        <div className="dk-hero__talk">
          <button className="bo-talk" aria-label="Start talking" onClick={onTalk} autoFocus>
            <Icon name="mic" fill /><span className="bo-talk__label">Talk</span>
          </button>
          <div className="bo-talk-meta">Free talk · {minutes} min</div>
          {freeLine && <p className="dk-hero__free" data-free-talks>{freeLine}</p>}
          {hasDay1 && <button className="dk-link dk-hero__day1" onClick={onPlayDay1}>Play my Day 1 clip</button>}
        </div>
      </div>
      <div className="dk-cols">
        <div>
          <h2 className="dk-h2">Next up</h2>
          {/* While Home loads (or can't), no card: a quick tap must not open practice when feedback is due (US-6.9). */}
          {home === null ? null : card?.kind === "feedback" ? (
            <button className="bo-homecard" data-home-card="feedback" onClick={() => onFeedback(card.talkId)}>
              <span className="bo-homecard__icon"><Icon name="forum" fill /></span>
              <span className="bo-homecard__body"><span className="bo-homecard__title">Finish your feedback</span>
                <span className="bo-homecard__sub">Your coach&apos;s feedback is ready</span></span>
              <Icon name="chevron_right" />
            </button>
          ) : card?.kind === "practice" ? (
            <button className="bo-homecard" data-home-card="practice" onClick={() => onPractice(card.pattern)}>
              <span className="bo-homecard__icon bo-homecard__icon--sun"><Icon name="record_voice_over" /></span>
              <span className="bo-homecard__body"><span className="bo-homecard__title">Practise {card.title.charAt(0).toLowerCase() + card.title.slice(1)}</span>
                <span className="bo-homecard__sub">From your last talk</span></span>
              <Icon name="chevron_right" />
            </button>
          ) : (
            <button className="bo-homecard" data-home-card="sounds" onClick={() => onPractice()}>
              <span className="bo-homecard__icon bo-homecard__icon--sun"><Icon name="record_voice_over" /></span>
              <span className="bo-homecard__body"><span className="bo-homecard__title">Practise sounds</span>
                <span className="bo-homecard__sub">Read sentences for one sound</span></span>
              <Icon name="chevron_right" />
            </button>
          )}
        </div>
        <div>
          <h2 className="dk-h2">This week <button className="dk-link" style={{ border: 0, background: "transparent", padding: 0 }} onClick={onProgress}>Progress</button></h2>
          {home?.week && <WeekCard week={home.week} />}
        </div>
      </div>
    </>
  );
}
