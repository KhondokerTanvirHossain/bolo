"use client";
import { useEffect, useRef, useState } from "react";
import { Icon, Scene } from "@/scene/Scene";
import { fetchReminder, notNow, saveReminder, type Reminder } from "./api";
import { askPermission, currentSupport, enablePush, type PushSupport } from "./push";
import { Preview } from "./Preview";
import { formatTime, icsFor, nearestSlot, SLOTS } from "./slots";
import { TimeChoice } from "./TimeChoice";

export type ReminderResult = { kind: "on" | "off" | "denied" | "iphone" | "calendar" | "skip"; time?: string };

/**
 * ScreenReminder / DesktopReminder (DS v43, PRG-5, US-6.5): "When should Bolo check in?" One reminder a day at the
 * learner's time. The browser's own permission box comes only after the tap. On an iPhone tab the time is saved and
 * ScreenReminderIphone explains Add to Home Screen instead.
 */
export function ReminderStep({ onDone, firstLoop = false }: { onDone: (r: ReminderResult) => void; firstLoop?: boolean }) {
  const [reminder, setReminder] = useState<Reminder | null>(null);
  const [time, setTime] = useState(() => SLOTS.find((s) => s.id === nearestSlot(new Date()))!.time);
  const [keepPrivate, setKeepPrivate] = useState(false);
  const [support, setSupport] = useState<PushSupport>("ok");
  const [desk, setDesk] = useState(false);
  const [touch, setTouch] = useState(false);
  const [phase, setPhase] = useState<"choose" | "iphone">("choose");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const done = useRef(false);
  const finish = (r: ReminderResult) => {
    if (done.current) return;
    done.current = true;
    onDone(r);
  };

  useEffect(() => {
    setSupport(currentSupport());
    setDesk(window.matchMedia?.("(min-width: 900px)").matches === true);
    setTouch((navigator.maxTouchPoints ?? 0) > 0);
    void fetchReminder().then((r) => {
      // Reminders are the account's: answered on another browser, a new browser's first talk doesn't ask again (review, I7).
      if (!r || (firstLoop && r.asked)) return finish({ kind: "skip" });
      if (r.asked) setTime(r.time);
      setReminder(r);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!reminder) return <section className="dk-screen on" data-screen="reminder"><Scene /><div className="dk-dim" /></section>;

  const remind = async () => {
    // The browser's box opens from the tap itself, before any await (Safari, review I8).
    const asked = support === "ok" ? askPermission() : null;
    setBusy(true);
    setProblem(null);
    const saved = await saveReminder({ on: true, time, keepPrivate });
    if (!saved) {
      setBusy(false);
      setProblem("That didn't save. Try again.");
      return;
    }
    if (support === "iphone-tab") {
      setBusy(false);
      setPhase("iphone");
      return;
    }
    if (support !== "ok" || !saved.publicKey) return finish({ kind: "denied", time });
    const r = await enablePush(saved.publicKey, asked);
    finish(r === "on" ? { kind: "on", time } : { kind: "denied", time });
  };
  const later = async () => {
    setBusy(true);
    await notNow();
    finish({ kind: "off" });
  };
  const calendar = async () => {
    const url = URL.createObjectURL(new Blob([icsFor(time)], { type: "text/calendar" }));
    const a = Object.assign(document.createElement("a"), { href: url, download: "bolo-daily-talk.ics" });
    a.click();
    URL.revokeObjectURL(url);
    await saveReminder({ on: false, time });
    finish({ kind: "calendar", time });
  };
  const noReminders = async () => {
    setBusy(true);
    await saveReminder({ on: false });
    finish({ kind: "off" });
  };

  return (
    <section className="dk-screen on" data-screen="reminder">
      <Scene />
      <div className="dk-dim" />
      {phase === "iphone" ? (
        <div className="dk-card bo-reminder" role="dialog" aria-modal="true" aria-labelledby="reminder-title">
          <IphoneSteps time={time} />
          <div className="bo-flow__actions" style={{ gap: 8, marginTop: 24 }}>
            <button className="bo-btn bo-btn--primary bo-btn--block" onClick={() => finish({ kind: "iphone", time })} autoFocus>Got it</button>
            <button className="bo-btn bo-btn--quiet-neutral" disabled={busy} onClick={() => void noReminders()}>No reminders</button>
          </div>
        </div>
      ) : (
        <div className="dk-card dk-card--wide bo-reminder" role="dialog" aria-modal="true" aria-labelledby="reminder-title">
          <h1 className="bo-flow__title" id="reminder-title">When should Bolo check in?</h1>
          <p className="bo-flow__body">{desk ? "One reminder a day in this browser, only if you haven't talked yet. Your browser will ask you once."
            : "One reminder a day, only if you haven't talked yet."}</p>
          {!desk && <p className="bo-flow__bn">দিনে একবার, শুধু যদি সেদিন কথা না বলেন।</p>}
          <div style={{ height: 16 }} />
          {!desk && <Preview time={time} text={keepPrivate ? "Time for today's talk." : reminder.preview} />}
          <div style={{ height: 16 }} />
          <TimeChoice time={time} desk={desk} onChange={setTime} />
          {desk && (
            <div className="dk-notifbox">
              <div className="micro" style={{ color: "var(--ink-secondary)", marginBottom: 8 }}>What you&apos;ll see</div>
              <Preview time={time} text={keepPrivate ? "Time for today's talk." : reminder.preview} />
              <div className="bo-row" style={{ padding: "14px 0 0" }}>
                <div className="bo-row__body"><div className="bo-row__title">Keep it private</div>
                  <div className="bo-row__sub">Show only &quot;Time for today&apos;s talk&quot;, never what we talked about</div></div>
                <button className="bo-toggle" role="switch" aria-checked={keepPrivate} aria-label="Keep it private" onClick={() => setKeepPrivate(!keepPrivate)} />
              </div>
            </div>
          )}
          <div className="bo-flow__actions" style={{ gap: 8, marginTop: 20 }}>
            {problem && <p className="bo-footnote" role="alert">{problem}</p>}
            {support === "ok" && <p className="bo-footnote">{touch && !desk ? "Next, your phone asks to allow notifications." : "Next, your browser asks to allow notifications."}</p>}
            <button className="bo-btn bo-btn--primary bo-btn--block" disabled={busy} onClick={() => void remind()}>
              {desk && <Icon name="notifications" size="sm" />}Remind me at {formatTime(time)}
            </button>
            {desk ? (
              <div className="dk-alt">
                <button className="dk-link" onClick={() => void calendar()}>Add to my calendar instead</button>
                <button className="dk-link dk-link--quiet" disabled={busy} onClick={() => void later()}>Not now</button>
              </div>
            ) : <button className="bo-btn bo-btn--quiet-neutral" disabled={busy} onClick={() => void later()}>Not now</button>}
          </div>
        </div>
      )}
    </section>
  );
}

/** ScreenReminderIphone (DS v43): iPhone sends reminders only to apps on the Home Screen. */
export function IphoneSteps({ time, compact }: { time: string; compact?: boolean }) {
  return (
    <>
      {!compact && <h1 className="bo-flow__title" id="reminder-title">One more step on iPhone</h1>}
      {!compact && <p className="bo-flow__body">iPhone sends reminders only to apps on your Home Screen.</p>}
      <div className="bo-group" style={{ marginTop: compact ? 8 : 20 }}>
        <div className="bo-row"><span className="bo-row__icon"><Icon name="ios_share" /></span><div className="bo-row__body"><div className="bo-row__title">1. Tap Share</div>
          <div className="bo-row__sub">In Chrome it&apos;s at the top right. In Safari, at the bottom.</div></div></div>
        <div className="bo-row"><span className="bo-row__icon"><Icon name="add_box" /></span><div className="bo-row__body"><div className="bo-row__title">2. Tap Add to Home Screen</div>
          <div className="bo-row__sub">Scroll down the list if you don&apos;t see it.</div></div></div>
        <div className="bo-row"><span className="bo-row__icon"><Icon name="touch_app" /></span><div className="bo-row__body"><div className="bo-row__title">3. Open Bolo from your Home Screen</div>
          <div className="bo-row__sub">Bolo asks there to send your reminder.</div></div></div>
      </div>
      {!compact && <p className="bo-memnote" style={{ marginTop: 16 }}><Icon name="schedule" size="sm" /> Your time is saved: {formatTime(time)}.</p>}
    </>
  );
}
