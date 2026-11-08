"use client";
import { useEffect, useState } from "react";
import { DEFAULT_SETTINGS, fetchSettings, saveSettings, type Settings } from "./settings";

/** US-9.5, Speaking group (P0 rows only): Bolo's speed and captions. Each choice saves straight away, for the next talk. */
export function SettingsCard({ onClose }: { onClose: () => void }) {
  const [s, setS] = useState<Settings | null>(null);
  const [failed, setFailed] = useState(false);
  const [unreadable, setUnreadable] = useState(false);
  useEffect(() => { void fetchSettings().then((v) => { if (v) setS(v); else setUnreadable(true); }); }, []);

  const change = async (next: Settings) => {
    setS(next);
    setFailed(!(await saveSettings(next)));
  };
  const cur = s ?? DEFAULT_SETTINGS;
  // The same chips as the setup questions: the chosen one is highlighted.
  const choice = (label: string, on: boolean, pick: () => void) => (
    <button className="bo-chip" style={{ justifyContent: "center" }} aria-pressed={on} onClick={pick} disabled={!s}>{label}</button>
  );
  return (
    <>
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="settings-title">
        <h1 className="bo-flow__title" id="settings-title">Settings</h1>
        <p className="bo-flow__body" style={{ margin: "4px 0 0" }}>Changes apply from your next talk.</p>
        <fieldset style={{ border: 0, padding: 0, margin: "20px 0 0" }}>
          <legend className="bo-fix__label">Bolo&apos;s speed</legend>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 8 }}>
            {choice("Normal", cur.buddySpeed === "normal", () => void change({ ...cur, buddySpeed: "normal" }))}
            {choice("Slower", cur.buddySpeed === "slower", () => void change({ ...cur, buddySpeed: "slower" }))}
          </div>
        </fieldset>
        <fieldset style={{ border: 0, padding: 0, margin: "16px 0 0" }}>
          <legend className="bo-fix__label">Captions</legend>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 8 }}>
            {choice("On", cur.captions, () => void change({ ...cur, captions: true }))}
            {choice("Off", !cur.captions, () => void change({ ...cur, captions: false }))}
          </div>
        </fieldset>
        {unreadable && <p className="bo-footnote" role="alert" style={{ marginTop: 12 }}>Settings can&apos;t load right now. Check your connection and try again.</p>}
        {failed && <p className="bo-footnote" role="alert" style={{ marginTop: 12 }}>That didn&apos;t save. Check your connection and try again.</p>}
        <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }} onClick={onClose} autoFocus>Done</button>
      </div>
    </>
  );
}
