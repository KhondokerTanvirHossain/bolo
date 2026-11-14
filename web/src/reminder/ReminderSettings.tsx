"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { fetchReminder, saveReminder, type Reminder } from "./api";
import { askPermission, currentSupport, enablePush, pushState, type PushSupport } from "./push";
import { Preview } from "./Preview";
import { IphoneSteps } from "./ReminderStep";
import { TimeChoice } from "./TimeChoice";

/** ScreenReminderSettings (DS v43): Me → Daily reminder. Every change saves at once; a failed save goes back. */
export function ReminderSettings({ onBack }: { onBack: () => void }) {
  const [r, setR] = useState<Reminder | null | undefined>(undefined);
  const [support, setSupport] = useState<PushSupport>("ok");
  const [here, setHere] = useState<"here" | "not-here" | "blocked">("not-here");
  const [failed, setFailed] = useState(false);
  const [how, setHow] = useState(false);
  const [touch, setTouch] = useState(false);
  useEffect(() => {
    setSupport(currentSupport());
    setTouch((navigator.maxTouchPoints ?? 0) > 0);
    void fetchReminder().then(setR);
    void pushState().then(setHere);
  }, []);

  const save = async (patch: Partial<Pick<Reminder, "on" | "time" | "keepPrivate">>) => {
    if (!r) return null;
    const before = r;
    setR({ ...r, ...patch });
    setFailed(false);
    const saved = await saveReminder(patch);
    if (!saved) {
      setR(before);
      setFailed(true);
      return null;
    }
    setR(saved);
    return saved;
  };
  const turnOnHere = async (key: string | null, asked?: Promise<NotificationPermission> | null) => {
    const res = await enablePush(key, asked);
    // "Blocked" only when the browser says so: a closed box is not a refusal (review I8).
    setHere(res === "on" ? "here" : typeof Notification !== "undefined" && Notification.permission === "denied" ? "blocked" : "not-here");
  };
  const toggle = async () => {
    if (!r) return;
    const asked = !r.on && support === "ok" && here === "not-here" ? askPermission() : null;
    const saved = await save({ on: !r.on });
    if (saved?.on && asked) await turnOnHere(saved.publicKey, asked);
  };

  const status = () => {
    if (support === "old-iphone") return <p className="bo-memnote">This iPhone can&apos;t get reminders from a website. Update iOS to get them.</p>;
    if (support === "iphone-tab") {
      return (
        <>
          <p className="bo-memnote">On iPhone, add Bolo to your Home Screen first. <button className="dk-link" onClick={() => setHow(!how)}>How?</button></p>
          {how && r && <IphoneSteps time={r.time} compact />}
        </>
      );
    }
    if (support === "unsupported") return <p className="bo-memnote">This browser can&apos;t show reminders.</p>;
    if (here === "blocked") return <p className="bo-memnote">Notifications are blocked for Bolo in this browser. Allow them in the browser&apos;s site settings.</p>;
    if (here === "not-here") {
      return <p className="bo-memnote">Not on for this browser yet. <button className="dk-link" onClick={() => r && void turnOnHere(r.publicKey)}>Turn on here</button></p>;
    }
    return <p className="bo-memnote">On for this {touch ? "phone" : "browser"}. Turn it on in each browser where you want reminders.</p>;
  };

  return (
    <section className="bo-voicemood">
      <button className="bo-back" onClick={onBack}><Icon name="chevron_left" />Me</button>
      <h1 className="dk-h1">Daily reminder</h1>
      {failed && <p className="bo-footnote" role="alert" style={{ textAlign: "left", marginBottom: 12 }}>That didn&apos;t save. Try again.</p>}
      {r === null && <p className="bo-footnote" role="alert">Your reminder can&apos;t load right now. Check your connection.</p>}
      {r && (
        <>
          <div className="bo-group">
            <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">Remind me</div>
              <div className="bo-row__sub">One a day, only if you haven&apos;t talked yet</div></div>
              <button className="bo-toggle" role="switch" aria-checked={r.on} aria-label="Remind me" onClick={() => void toggle()} /></div>
          </div>
          {r.on && (
            <>
              <div style={{ height: 16 }} />
              <TimeChoice time={r.time} onChange={(time) => void save({ time })} />
              <div style={{ height: 12 }} />
              <div className="bo-group">
                <div className="bo-row"><div className="bo-row__body"><div className="bo-row__title">Keep it private</div>
                  <div className="bo-row__sub">Show only &quot;Time for today&apos;s talk&quot;, never what you talked about</div></div>
                  <button className="bo-toggle" role="switch" aria-checked={r.keepPrivate} aria-label="Keep it private" onClick={() => void save({ keepPrivate: !r.keepPrivate })} /></div>
              </div>
              <div style={{ height: 16 }} />
              <Preview time={r.time} text={r.keepPrivate ? "Time for today's talk." : r.preview} />
              <div style={{ marginTop: 16 }}>{status()}</div>
            </>
          )}
        </>
      )}
    </section>
  );
}
