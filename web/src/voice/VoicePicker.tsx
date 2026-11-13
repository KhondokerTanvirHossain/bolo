"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import type { Settings } from "@/settings/settings";
import { ACCENTS, GENDERS, playSample, stopSample, voiceKey } from "./voices";

/**
 * Bolo's voice (P3c, SET-5; design system v38 ScreenVoicePicker): Female or Male, then British, American or Indian.
 * "Hear it" plays that voice's sample (one at a time) and doesn't choose it. Choosing saves at once, for the next talk.
 */
export function VoicePicker({ settings, onChange }: { settings: Settings; onChange: (next: Settings) => void }) {
  const [playing, setPlaying] = useState<string | null>(null);
  useEffect(() => () => stopSample(), []);
  const gender = settings.voiceGender ?? "female";
  const accent = settings.voiceAccent ?? "british";
  const hear = (key: string) => {
    if (playing === key) {
      stopSample();
      return;
    }
    setPlaying(key);
    playSample(key, () => setPlaying((p) => (p === key ? null : p)));
  };
  return (
    <div className="bo-voicepicker">
      <div className="bo-chips" role="radiogroup" aria-label="Voice" style={{ marginBottom: 16 }}>
        {GENDERS.map((g) => (
          <button key={g.value} className="bo-chip" role="radio" aria-checked={gender === g.value} aria-pressed={gender === g.value}
            onClick={() => gender !== g.value && onChange({ ...settings, voiceGender: g.value })}>{g.label}</button>
        ))}
      </div>
      <h3 className="bo-group__title">English</h3>
      <div className="bo-group" role="radiogroup" aria-label="Accent">
        {ACCENTS.map((a) => {
          const key = voiceKey(settings, a.value, gender);
          const on = playing === key;
          return (
            <div className="bo-voice" key={a.value}>
              <button className="bo-voice__pick" role="radio" aria-checked={accent === a.value}
                onClick={() => accent !== a.value && onChange({ ...settings, voiceAccent: a.value })}>
                <span className="bo-radio" />
                <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>{a.label}</span><span className="bo-row__sub">{a.sub}</span></span>
              </button>
              <button className="bo-voice__hear" aria-pressed={on} aria-label={`Hear the ${a.label} ${gender} voice`} onClick={() => hear(key)}>
                <Icon name={on ? "graphic_eq" : "play_arrow"} />{on ? "Playing" : "Hear it"}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
