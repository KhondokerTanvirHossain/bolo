"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { fetchSettings, saveSettings, type Settings } from "@/settings/settings";
import { MoodPicker } from "./MoodPicker";
import { VoicePicker } from "./VoicePicker";

/** Me → Settings → Bolo's voice / Bolo's mood on a phone (P3c; design system v38 ScreenVoicePicker, ScreenMoodPicker). */
export function VoiceMoodScreen({ kind, onBack }: { kind: "voice" | "mood"; onBack: () => void }) {
  const [s, setS] = useState<Settings | null | undefined>(undefined);
  const [failed, setFailed] = useState(false);
  useEffect(() => { void fetchSettings().then(setS); }, []);
  const change = async (next: Settings) => {
    const before = s;
    setS(next);
    setFailed(false);
    if (!(await saveSettings(next))) {
      setS(before);
      setFailed(true);
    }
  };
  return (
    <section className="bo-voicemood">
      <button className="bo-back" onClick={onBack}><Icon name="chevron_left" />Settings</button>
      <h1 className="dk-h1">{kind === "voice" ? "Bolo's voice" : "Bolo's mood"}</h1>
      <p className="bo-memnote">{kind === "voice" ? "Pick who Bolo sounds like. You'll hear it from your next talk."
        : "How Bolo talks with you. It works with every voice, from your next talk."}</p>
      {failed && <p className="bo-footnote" role="alert" style={{ textAlign: "left", marginBottom: 12 }}>That didn&apos;t save. Try again.</p>}
      {s === null && <p className="bo-footnote" role="alert">Settings can&apos;t load right now. Check your connection.</p>}
      {s && (kind === "voice" ? <VoicePicker settings={s} onChange={(n) => void change(n)} /> : <MoodPicker settings={s} onChange={(n) => void change(n)} />)}
      {s && kind === "voice" && <p className="bo-footnote" style={{ marginTop: 24 }}>Your Coach keeps its own voice. How Bolo understands you doesn&apos;t change.</p>}
    </section>
  );
}
