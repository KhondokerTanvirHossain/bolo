"use client";
import type { Settings } from "@/settings/settings";
import { ANSWER_TIMES, autoSpeedLabel } from "./voices";

/** Time to answer (AX-7, changed 2026-11-14; design system v41 ScreenAnswerTime): saves at once, for the next talk. */
export function AnswerTimePicker({ settings, onChange }: { settings: Settings; onChange: (next: Settings) => void }) {
  const chosen = settings.answerTime ?? "auto";
  return (
    <div className="bo-group" role="radiogroup" aria-label="Time to answer">
      {ANSWER_TIMES.map((a) => (
        <button key={a.value} className="bo-mood" role="radio" aria-checked={chosen === a.value}
          onClick={() => chosen !== a.value && onChange({ ...settings, answerTime: a.value })}>
          <span className="bo-radio" />
          <span className="bo-row__body">
            <span className="bo-row__title" style={{ display: "block" }}>{a.label}</span>
            <span className="bo-row__sub" style={{ display: "block" }}>
              {a.value === "auto" ? `${a.desc} Right now: ${autoSpeedLabel(settings)}` : a.desc}
            </span>
          </span>
        </button>
      ))}
    </div>
  );
}
