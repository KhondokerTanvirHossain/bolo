"use client";
import { useRef, useState } from "react";
import { Icon } from "@/scene/Scene";
import { useDialog } from "@/ui/useDialog";
import { saveRating } from "./rating";

const FACES = [
  { score: 1, label: "Bad", icon: "sentiment_very_dissatisfied" },
  { score: 2, label: "Not great", icon: "sentiment_dissatisfied" },
  { score: 3, label: "Okay", icon: "sentiment_neutral" },
  { score: 4, label: "Good", icon: "sentiment_satisfied" },
  { score: 5, label: "Great", icon: "sentiment_very_satisfied" },
];

/**
 * DBF-8 (design system v40 ScreenRating): "How was today?" One tap saves the score at once; an optional typed note
 * (≤ 300) goes with Send. Not now, Esc or closing before a tap saves a skip. A failed save closes quietly.
 * Phone: a bottom sheet; desktop: a centred dialog.
 */
export function RatingSheet({ talkId, onClose }: { talkId: string; onClose: () => void }) {
  const [score, setScore] = useState<number | null>(null);
  const [note, setNote] = useState("");
  const box = useRef<HTMLDivElement>(null);
  const done = useRef(false);
  const close = (send: boolean) => {
    if (done.current) return;
    done.current = true;
    if (score === null) void saveRating(talkId, null);
    else if (send && note.trim()) void saveRating(talkId, score, note.trim());
    onClose();
  };
  useDialog(box, () => close(false));
  const pick = (n: number) => {
    setScore(n);
    void saveRating(talkId, n);
  };
  return (
    <>
      <div className="dk-dim" onClick={() => close(false)} />
      <div className="bo-rate" ref={box} role="dialog" aria-modal="true" aria-labelledby="rate-title">
        <div className="bo-sheet__grabber" aria-hidden="true" />
        <p className="bo-sheet__title" id="rate-title">How was today?</p>
        <div className="bo-faces" role="radiogroup" aria-label="How was today?">
          {FACES.map((f) => (
            <button key={f.score} className="bo-face" role="radio" aria-checked={score === f.score} aria-label={f.label} onClick={() => pick(f.score)}>
              <Icon name={f.icon} />{f.label}
            </button>
          ))}
        </div>
        {score !== null && (
          <>
            <label className="bo-row__sub" htmlFor="rate-note" style={{ fontSize: 14 }}>Want to tell us why? (optional)</label>
            <textarea id="rate-note" className="bo-note" maxLength={300} value={note} onChange={(e) => setNote(e.target.value)}
              placeholder="Bolo asked good questions about my demo" />
          </>
        )}
        <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
          {score !== null && <button className="bo-btn bo-btn--primary" style={{ flex: 1 }} onClick={() => close(true)}>Send</button>}
          <button className="bo-btn bo-btn--quiet-neutral" style={score === null ? { marginLeft: "auto" } : undefined} onClick={() => close(false)}>Not now</button>
        </div>
      </div>
    </>
  );
}
