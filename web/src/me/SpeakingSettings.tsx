"use client";
import { useEffect, useState } from "react";
import { fetchSettings, saveSettings, type Settings } from "@/settings/settings";

/**
 * US-9.5 Speaking group (P0 rows): Bolo's speed, captions and the talk view (P3a), as chips. Each change saves at once,
 * for the next talk.
 */
export function SpeakingSettings() {
  const [s, setS] = useState<Settings | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "unreadable" | "failed">("loading");
  useEffect(() => { void fetchSettings().then((v) => { if (v) { setS(v); setState("ready"); } else setState("unreadable"); }); }, []);
  const change = async (next: Settings) => {
    setS(next);
    setState((await saveSettings(next)) ? "ready" : "failed");
  };
  const chip = (label: string, on: boolean, pick: () => void) => (
    <button className="bo-chip" style={{ justifyContent: "center" }} aria-pressed={on} onClick={pick} disabled={!s}>{label}</button>
  );
  return (
    <div className="bo-group">
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
  );
}
