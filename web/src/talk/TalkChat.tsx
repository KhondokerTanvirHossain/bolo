"use client";
import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { Icon } from "@/scene/Scene";
import type { ChatTurn } from "./talkState";

/** Scrolled further than this from the bottom, the chat stops following the newest line. */
const AWAY_PX = 40;

/**
 * TalkChat (design system v33; TLK-8 changed 2026-11-12): the talk as a live chat in the sky above the companion.
 * - Bolo's turns are on the left and the learner's on the right.
 * - The learner's live words sit in a dashed bubble, hidden from screen readers (only settled turns are announced).
 * - It follows the newest line unless the learner scrolled up; then "Latest" brings it back.
 */
export function TalkChat({ chat, live, replay, status, canRepeat, onRepeat }: {
  chat: ChatTurn[];
  live: { turn: number; text: string } | null;
  replay: number | null;
  status: ReactNode;
  canRepeat: boolean;
  onRepeat: () => void;
}) {
  const box = useRef<HTMLDivElement>(null);
  const [following, setFollowing] = useState(true);

  useLayoutEffect(() => {
    const b = box.current;
    if (b && following) b.scrollTop = b.scrollHeight;
  });

  const onScroll = () => {
    const b = box.current;
    if (!b) return;
    setFollowing(b.scrollHeight - b.scrollTop - b.clientHeight <= AWAY_PX);
  };
  const latest = () => {
    setFollowing(true);
    const b = box.current;
    if (b) b.scrollTop = b.scrollHeight;
  };

  const lastBolo = [...chat].reverse().find((t) => t.who === "bolo")?.turn;
  return (
    <div className="bo-chat" ref={box} onScroll={onScroll}>
      <div className="bo-chat__log" role="log" aria-live="polite" aria-label="Conversation">
        {chat.map((t) => t.who === "bolo" ? (
          <div key={`b${t.turn}`} className={`bo-chat__turn bo-chat__turn--bolo${replay === t.turn ? " bo-chat__turn--replay" : ""}`}>
            <p className="bo-chat__text"><span className="bo-chat__who">Bolo</span>{t.text}</p>
            {canRepeat && t.turn === lastBolo && (
              <button className="bo-caption__replay" aria-label="Repeat that" onClick={onRepeat}><Icon name="replay" size="sm" /></button>
            )}
          </div>
        ) : (
          <div key={`y${t.turn}`} className="bo-chat__turn bo-chat__turn--you">
            <span className="bo-chat__who">You</span><p className="bo-chat__text">{t.text}</p>
          </div>
        ))}
      </div>
      {live && (
        <div className="bo-chat__turn bo-chat__turn--you bo-chat__turn--live" aria-hidden="true">
          <span className="bo-chat__who">You · speaking</span><p className="bo-chat__text">{live.text}</p>
        </div>
      )}
      {status && <p className="bo-chat__status" role="status">{status}</p>}
      {!following && (
        <button className="bo-chat__latest" onClick={latest}><Icon name="arrow_downward" size="sm" />Latest</button>
      )}
    </div>
  );
}
