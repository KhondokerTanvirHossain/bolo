"use client";
import { Icon } from "@/scene/Scene";
import { segments, type Chat } from "./chat";

/**
 * See full chat (DBF-6, US-4.10): the talk as bubbles, Bolo on the left and you on the right. The spots your coach fixed
 * are underlined; each opens its fix card, where its clip plays (the whole talk's audio isn't kept).
 */
export function ChatScreen({ chat, onBack, onOpenFix }: { chat: Chat; onBack: () => void; onOpenFix: (fix: number) => void }) {
  return (
    <div className="dk-chat">
      <div className="dk-chat__head">
        <button className="bo-btn bo-btn--quiet-neutral" style={{ padding: 0 }} onClick={onBack}><Icon name="arrow_back" size="sm" />Back to feedback</button>
        <h2 className="dk-h2" style={{ margin: "8px 0 0" }}>Your talk</h2>
        <p className="bo-row__sub" style={{ margin: 0 }}>{chat.topic}</p>
      </div>
      <ol className="dk-chat__list">
        {chat.turns.map((t, i) => {
          const you = t.speaker === "learner";
          return (
            <li key={i} className={you ? "dk-bubble dk-bubble--you" : "dk-bubble"}>
              <span className="dk-bubble__who">{you ? "You" : "Bolo"}</span>
              <p className="dk-bubble__text">
                {segments(t.text, chat.marks.filter((m) => m.turn === i)).map((s, j) => s.mark ? (
                  <button key={j} className="dk-mark" aria-label={`"${s.text}": ${s.mark.title}`} onClick={() => onOpenFix(s.mark!.fix)}>{s.text}</button>
                ) : <span key={j}>{s.text}</span>)}
              </p>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
