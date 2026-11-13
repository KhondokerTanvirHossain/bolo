"use client";
import { useEffect, useState } from "react";
import { fetchSettings, saveSettings, type Settings } from "@/settings/settings";
import { Icon } from "@/scene/Scene";
import { MoodPicker } from "@/voice/MoodPicker";
import { VoicePicker } from "@/voice/VoicePicker";
import { ANSWER_TIMES, answerTimeSummary, autoSpeedLabel, moodLabel, voiceSummary } from "@/voice/voices";

/**
 * US-9.5 Speaking group (P0 rows): Bolo's speed, captions and the talk view (P3a), as chips. Each change saves at once,
 * for the next talk. P3c (SET-5, SET-6; design system v38): Bolo's voice and mood come first. On a phone they are rows
 * that open their pickers; on desktop (DesktopVoiceMood) the pickers sit here, side by side.
 */
export function SpeakingSettings({ onVoice, onMood, onAnswerTime }: { onVoice?: () => void; onMood?: () => void; onAnswerTime?: () => void } = {}) {
  const [s, setS] = useState<Settings | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "unreadable" | "failed">("loading");
  useEffect(() => { void fetchSettings().then((v) => { if (v) { setS(v); setState("ready"); } else setState("unreadable"); }); }, []);
  const change = async (next: Settings) => {
    const before = s;
    setS(next);
    if (await saveSettings(next)) setState("ready");
    else {
      // Review (P3c): show what is really saved, not the choice that failed.
      setS(before);
      setState("failed");
    }
  };
  const chip = (label: string, on: boolean, pick: () => void) => (
    <button className="bo-chip" style={{ justifyContent: "center" }} aria-pressed={on} onClick={pick} disabled={!s}>{label}</button>
  );
  const open = (title: string, value: string, go?: () => void) => (
    <button className="bo-row" style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }}
      onClick={go} disabled={!go}>
      <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>{title}</span></span>
      <span className="bo-row__value">{value}</span>
      <Icon name="chevron_right" />
    </button>
  );
  return (
    <>
    {s && (
      <div className="bo-speak__inline">
        <div>
          <h3 className="bo-group__title">Bolo&apos;s voice</h3>
          <VoicePicker settings={s} onChange={(n) => void change(n)} />
          <p className="bo-row__sub" style={{ margin: "10px 0 0" }}>Your Coach keeps its own voice.</p>
        </div>
        <div>
          <h3 className="bo-group__title">Bolo&apos;s mood</h3>
          <MoodPicker settings={s} onChange={(n) => void change(n)} />
        </div>
      </div>
    )}
    <div className="bo-group">
      <div className="bo-speak__rows">
        {open("Bolo's voice", s ? voiceSummary(s) : "", onVoice)}
        {open("Bolo's mood", s ? moodLabel(s) : "", onMood)}
        {open("Time to answer", s ? answerTimeSummary(s) : "", onAnswerTime)}
      </div>
      {/* AX-7 (v42 DesktopMe): on desktop the four choices sit in the row. */}
      <div className="bo-row bo-speak__desk">
        <div className="bo-row__body"><div className="bo-row__title">Time to answer</div>
          <div className="bo-row__sub">Automatic matches how long you take to start. Right now: {s ? autoSpeedLabel(s) : ""}</div></div>
        <div role="radiogroup" aria-label="Time to answer" style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {ANSWER_TIMES.map((a) => (
            <button key={a.value} className="bo-chip" role="radio" aria-checked={(s?.answerTime ?? "auto") === a.value && !!s} disabled={!s}
              onClick={() => s && (s.answerTime ?? "auto") !== a.value && void change({ ...s, answerTime: a.value })}>{a.label}</button>
          ))}
        </div>
      </div>
      <div className="bo-row">
        <div className="bo-row__body"><div className="bo-row__title">I stammer</div>
          <div className="bo-row__sub">Bolo waits through repeats and pauses, and your feedback won&apos;t count them</div></div>
        <button className="bo-toggle" role="switch" aria-checked={s?.stammer === true} aria-label="I stammer" disabled={!s}
          onClick={() => s && void change({ ...s, stammer: !s.stammer })} />
      </div>
      <div className="bo-row">
        <div className="bo-row__body"><div className="bo-row__title">Bolo&apos;s speed</div><div className="bo-row__sub">From your next talk</div></div>
        <div style={{ display: "flex", gap: 8 }}>
          {chip("Normal", s?.buddySpeed === "normal", () => s && void change({ ...s, buddySpeed: "normal" }))}
          {chip("Slower", s?.buddySpeed === "slower", () => s && void change({ ...s, buddySpeed: "slower" }))}
        </div>
      </div>
      <div className="bo-row">
        <div className="bo-row__body"><div className="bo-row__title">Talk view</div><div className="bo-row__sub">Chat shows both sides; Classic shows Bolo only</div></div>
        <div style={{ display: "flex", gap: 8 }}>
          {chip("Chat", (s?.talkView ?? "chat") === "chat" && !!s, () => s && void change({ ...s, talkView: "chat" }))}
          {chip("Classic", s?.talkView === "classic", () => s && void change({ ...s, talkView: "classic" }))}
        </div>
      </div>
      <div className="bo-row">
        <div className="bo-row__body"><div className="bo-row__title">Captions</div><div className="bo-row__sub">Bolo&apos;s words on screen</div></div>
        <div style={{ display: "flex", gap: 8 }}>
          {chip("On", s?.captions === true, () => s && void change({ ...s, captions: true }))}
          {chip("Off", s?.captions === false, () => s && void change({ ...s, captions: false }))}
        </div>
      </div>
      {state === "unreadable" && <p className="bo-footnote" role="alert" style={{ padding: "0 20px 12px" }}>Settings can&apos;t load right now. Check your connection.</p>}
      {state === "failed" && <p className="bo-footnote" role="alert" style={{ padding: "0 20px 12px" }}>That didn&apos;t save. Try again.</p>}
    </div>
    </>
  );
}
