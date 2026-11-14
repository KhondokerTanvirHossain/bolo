"use client";
import { useRef, useState } from "react";
import { useDialog } from "@/ui/useDialog";
import type { Reminder } from "./api";
import { enablePush } from "./push";
import { Preview } from "./Preview";
import { formatTime } from "./slots";

/**
 * ScreenReminderTurnOn (DS v43): Bolo opened from the iPhone Home Screen with a saved time and no permission yet.
 * Asked once; the tap is what iOS needs before it shows its own box.
 */
export function ReminderTurnOn({ reminder, onClose }: { reminder: Reminder; onClose: (r: "on" | "denied" | "later") => void }) {
  const box = useRef<HTMLDivElement>(null);
  const [busy, setBusy] = useState(false);
  useDialog(box, () => onClose("later"));
  const turnOn = async () => {
    setBusy(true);
    const r = await enablePush(reminder.publicKey);
    onClose(r === "on" ? "on" : "denied");
  };
  return (
    <>
      <div className="dk-dim" onClick={() => onClose("later")} />
      <div className="bo-rate" ref={box} role="dialog" aria-modal="true" aria-labelledby="turnon-title">
        <div className="bo-sheet__grabber" aria-hidden="true" />
        <p className="bo-sheet__title" id="turnon-title">Turn on your reminder?</p>
        <p className="bo-sheet__body">Bolo checks in at {formatTime(reminder.time)}, only if you haven&apos;t talked yet.</p>
        <div style={{ margin: "12px 0" }}><Preview time={reminder.time} text={reminder.keepPrivate ? "Time for today's talk." : reminder.preview} /></div>
        <p className="bo-footnote">Next, your iPhone asks to allow notifications.</p>
        <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
          <button className="bo-btn bo-btn--primary" style={{ flex: 1 }} disabled={busy} onClick={() => void turnOn()}>Turn on reminders</button>
          <button className="bo-btn bo-btn--quiet-neutral" onClick={() => onClose("later")}>Not now</button>
        </div>
      </div>
    </>
  );
}
