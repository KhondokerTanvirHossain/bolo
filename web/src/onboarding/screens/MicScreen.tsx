"use client";
import { useEffect, useState } from "react";
import { classifyMicCheck, stickyVerdict, type MicCheckResult } from "@/audio/micCheck";
import { unblockGuidance } from "@/audio/micErrors";
import { useMicLevel } from "@/audio/useMicLevel";
import { Icon, Scene } from "@/scene/Scene";

type Props = {
  deviceId: string | null;
  headphones: boolean;
  onChooseMic: (id: string) => void;
  onForgetMic: () => void;
  onHeadphones: (on: boolean) => void;
  onContinue: (verdict: "great" | "noisy" | "quiet" | "skipped") => void;
};

const BARS = [6, 10, 18, 28, 40, 52, 60, 66, 58, 46, 34, 26, 18, 12, 8];

/** Bars light from the centre outwards as the level rises from -60 to -10 dBFS. */
function litBars(levelDb: number): Set<number> {
  const fraction = Math.min(1, Math.max(0, (levelDb + 60) / 50));
  const half = Math.round(fraction * Math.ceil(BARS.length / 2));
  const mid = Math.floor(BARS.length / 2);
  const lit = new Set<number>();
  for (let i = 0; i < half; i++) { lit.add(mid - i); lit.add(mid + i); }
  return lit;
}

const RESULT_COPY = {
  idle: { text: "Read the line out loud when you're ready.", icon: "mic" },
  great: { text: "Sounds great", icon: "check_circle" },
  noisy: { text: "It's a bit noisy. Earbuds or a quieter spot will help.", icon: "info" },
  quiet: { text: "Speak a little louder, or move closer to the mic.", icon: "info" },
} as const;

/** "Choose your microphone" (prototype step 2 of 5): picker, live level, read-aloud check (US-1.2, US-1.3, US-1.5). */
export function MicScreen({ deviceId, headphones, onChooseMic, onForgetMic, onHeadphones, onContinue }: Props) {
  const mic = useMicLevel(deviceId, onForgetMic);
  const live = classifyMicCheck(mic.history);
  const [result, setResult] = useState<MicCheckResult>("idle");
  useEffect(() => { setResult((prev) => stickyVerdict(prev, live)); }, [live]);
  const [guidance, setGuidance] = useState("");
  useEffect(() => { setGuidance(unblockGuidance(navigator.userAgent)); }, []);
  const lit = litBars(mic.levelDb);
  const copy = RESULT_COPY[result];

  return (
    <section className="dk-screen on" data-screen="mic">
      <Scene />
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="mic-title" data-mic-status={mic.status} data-mic-devices={mic.devices.length}>
        <div className="dk-steps"><span>Microphone</span><span>2 of 5</span></div>
        {mic.status === "blocked" ? (
          <>
            <h1 className="bo-flow__title" id="mic-title">Bolo needs your mic to hear you</h1>
            <p className="bo-flow__body">{guidance} Bolo carries on by itself.</p>
            <p className="bo-flow__bn">ঠিকানা বারের পাশের আইকনে ক্লিক করে মাইক্রোফোন চালু করুন।</p>
            <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }} onClick={mic.retry} autoFocus>
              Try again
            </button>
          </>
        ) : mic.status === "busy" ? (
          <>
            <h1 className="bo-flow__title" id="mic-title">Another app is using your mic</h1>
            <p className="bo-flow__body">Close Zoom, Teams or any other call, then come back. Bolo carries on by itself.</p>
            <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }} onClick={mic.retry} autoFocus>
              Try again
            </button>
          </>
        ) : mic.status === "no-device" ? (
          <>
            <h1 className="bo-flow__title" id="mic-title">No microphone found</h1>
            <p className="bo-flow__body">Plug in a headset or turn on your laptop&apos;s microphone, then try again.</p>
            <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }} onClick={mic.retry} autoFocus>
              Try again
            </button>
          </>
        ) : (
          <>
            <h1 className="bo-flow__title" id="mic-title">Choose your microphone</h1>
            <p className="bo-flow__body">
              Read this out loud: <b style={{ color: "var(--ink)" }}>&quot;I&apos;m ready to practise speaking today.&quot;</b>
            </p>
            <label className="dk-select" style={{ marginTop: 20 }}>
              <Icon name="headset_mic" />
              <select aria-label="Microphone" value={mic.activeDeviceId ?? ""} onChange={(e) => onChooseMic(e.target.value)}>
                {mic.devices.map((d, i) => (
                  <option key={d.deviceId || i} value={d.deviceId}>{d.label || `Microphone ${i + 1}`}</option>
                ))}
              </select>
              <Icon name="expand_more" />
            </label>
            <div className="bo-level" style={{ marginTop: 18 }} aria-hidden="true">
              {BARS.map((h, i) => <i key={i} className={lit.has(i) ? "on" : ""} style={{ height: h }} />)}
            </div>
            <p className={`bl-result bl-result--${result}`} role="status" aria-live="polite">
              <Icon name={copy.icon} size="sm" />{copy.text}
            </p>
            <div className="dk-tip" style={{ marginTop: 14 }}>
              <Icon name="headphones" />
              <span>
                Using laptop speakers? Headphones stop Bolo hearing itself, and keep your talk private.
                <label className="bl-tip-check">
                  <input type="checkbox" checked={headphones} onChange={(e) => onHeadphones(e.target.checked)} />
                  I&apos;m using headphones
                </label>
              </span>
            </div>
            <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }}
              onClick={() => onContinue(result === "idle" ? "skipped" : result)}>
              {result === "great" ? "Sounds good, continue" : result === "idle" ? "Continue" : "Continue anyway"}
            </button>
            <p className="bo-footnote" style={{ marginTop: 12 }}>
              Your browser asked for the mic once. Bolo listens only while you practise.
            </p>
          </>
        )}
      </div>
    </section>
  );
}
