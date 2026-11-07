"use client";
import { useEffect } from "react";
import { Scene } from "@/scene/Scene";
import { currentQuestion, isAnswered, type OnboardingState } from "../steps";

type Props = {
  state: OnboardingState;
  busy: boolean;
  failed: boolean;
  onAnswer: (value: string) => void;
  onNext: () => void;
  onBack: () => void;
};

/** "Bolo asks" (prototype setup card): one question at a time, chips or keys 1–4, no typing (R3). */
export function SetupScreen({ state, busy, failed, onAnswer, onNext, onBack }: Props) {
  const q = currentQuestion(state);
  const chosen = state.answers[q.field];

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLElement && e.target.closest("input, textarea, select")) return;
      const n = Number(e.key);
      const chip = Number.isInteger(n) && n >= 1 ? q.chips[n - 1] : undefined;
      if (chip) { e.preventDefault(); onAnswer(chip.value); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [q, onAnswer]);

  return (
    <section className="dk-screen on" data-screen="setup">
      <Scene />
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="setup-title">
        <div className="dk-steps">
          <span>
            <span className="bo-buddy-mark" style={{ width: 22, height: 22, fontSize: 11, marginRight: 6 }}>B</span>Bolo asks
          </span>
          <span>{state.questionIndex + 3} of 5</span>
        </div>
        <h1 className="bo-flow__title" id="setup-title">{q.en}</h1>
        <p className="bo-flow__bn" style={{ marginTop: 0 }}>{q.bn}</p>
        <p className="bo-footnote" style={{ textAlign: "left", margin: "18px 0 10px" }}>
          Say it, or click one. Keys 1–{q.chips.length} work too.
        </p>
        <div className="bo-chips" role="group" aria-label={q.en}>
          {q.chips.map((c) => (
            <button key={c.value} className="bo-chip" aria-pressed={chosen === c.value} onClick={() => onAnswer(c.value)}>
              {c.label}
            </button>
          ))}
        </div>
        <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 28 }}
          disabled={!isAnswered(state) || busy} onClick={onNext}>
          Next
        </button>
        {failed && (
          <p className="bl-result" role="alert">That didn&apos;t save. Check your connection, then press Next again.</p>
        )}
        {state.questionIndex > 0 && (
          <button className="bl-quiet-link" onClick={onBack}>Back</button>
        )}
      </div>
    </section>
  );
}
