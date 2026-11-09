"use client";
import { Icon, Scene } from "@/scene/Scene";
import { WORDMARK_SMALL_SVG } from "@/scene/art";
import type { Home } from "./home";
import { counterLine, freeWeekOverNote } from "@/plan/freeTalks";
import { useMemo } from "react";

type Props = {
  /** null while loading or when Home couldn't load: then only the Talk button shows. */
  home: Home | null;
  hasDay1: boolean;
  onTalk: () => void;
  onPractice: (pattern?: string) => void;
  onFeedback: (talkId: string) => void;
  onSettings: () => void;
  onPlayDay1: () => void;
  /** A one-off line in place of the usual caption ("See you tomorrow" after the first loop). */
  note?: string;
};

/**
 * Home (HOM-1, US-6.9, R1): one primary button (Talk) and at most one card — finish a feedback you haven't seen, or
 * practise its sound. Everything else is a quiet scene button.
 */
export function HomeScreen({ home, hasDay1, onTalk, onPractice, onFeedback, onSettings, onPlayDay1, note }: Props) {
  const card = home?.card ?? null;
  const free = home?.freeTalks ?? null;
  // ScreenHomeFree: one footnote line; the day the free week ends, it says so once.
  const freeLine = useMemo(() => (free ? freeWeekOverNote(free) ?? counterLine(free) : null), [free]);
  return (
    <section className="dk-screen on" data-screen="talk">
      <Scene />
      <div className="dk-talk__top">
        <span style={{ display: "flex", height: 28 }} dangerouslySetInnerHTML={{ __html: WORDMARK_SMALL_SVG }} />
        <span />
        <button className="bo-scenebtn" onClick={onSettings}><Icon name="person" size="sm" />Me</button>
      </div>
      <div className="dk-talk__cap">
        <div className="bo-caption" aria-live="polite">
          <p className="bo-caption__text">
            {note ?? <>Ready when you are. Bolo talks out loud, so turn your sound on.
              {hasDay1 && <>{" "}<span className="dim">Your Day 1 clip is saved on this device.</span></>}</>}
          </p>
        </div>
      </div>
      {card && (
        <div className="bo-card" data-home-card={card.kind}
          style={{ position: "absolute", left: "50%", bottom: 150, transform: "translateX(-50%)", width: "min(420px, calc(100% - 32px))",
            zIndex: 2, padding: "16px 18px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
          {card.kind === "feedback" ? (
            <>
              <span className="subhead">Your coach&apos;s feedback is ready.</span>
              <button className="bo-btn bo-btn--secondary" onClick={() => onFeedback(card.talkId)}>Finish your feedback</button>
            </>
          ) : (
            <>
              <span className="subhead">From your last talk</span>
              <button className="bo-btn bo-btn--secondary" onClick={() => onPractice(card.pattern)}>
                Practise {card.title.charAt(0).toLowerCase() + card.title.slice(1)}
              </button>
            </>
          )}
        </div>
      )}
      <div className="dk-dock">
        <button className="bo-btn bo-btn--primary" onClick={onTalk} autoFocus>
          <Icon name="mic" fill />Start talking
        </button>
        <button className="bo-scenebtn" onClick={() => onPractice()}>
          <Icon name="record_voice_over" />Practise sounds
        </button>
        {hasDay1 && (
          <button className="bo-scenebtn" onClick={onPlayDay1}>
            <Icon name="play_arrow" fill />Play my Day 1 clip
          </button>
        )}
      </div>
      {freeLine && <p className="dk-freeline" data-free-talks>{freeLine}</p>}
    </section>
  );
}
