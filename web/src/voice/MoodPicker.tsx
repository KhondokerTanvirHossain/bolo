"use client";
import type { Settings } from "@/settings/settings";
import { MOODS } from "./voices";

/** Bolo's mood (P3c, SET-6; design system v38 ScreenMoodPicker): words and manner only; saves at once, for the next talk. */
export function MoodPicker({ settings, onChange }: { settings: Settings; onChange: (next: Settings) => void }) {
  const mood = settings.mood ?? "friendly";
  return (
    <div className="bo-group" role="radiogroup" aria-label="Bolo's mood">
      {MOODS.map((m) => (
        <button key={m.value} className="bo-mood" role="radio" aria-checked={mood === m.value}
          onClick={() => mood !== m.value && onChange({ ...settings, mood: m.value })}>
          <span className="bo-radio" />
          <span className="bo-row__body">
            <span className="bo-row__title" style={{ display: "block" }}>{m.label}</span>
            <span className="bo-row__sub" style={{ display: "block" }}>{m.desc}</span>
            <span className="bo-mood__example">“{m.example}”</span>
          </span>
        </button>
      ))}
    </div>
  );
}
