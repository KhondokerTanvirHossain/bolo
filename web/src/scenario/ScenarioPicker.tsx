"use client";
import { useRef, useState, type KeyboardEvent } from "react";
import Link from "next/link";
import { Icon } from "@/scene/Scene";
import { useDialog } from "@/ui/useDialog";
import type { Difficulty, Scenario } from "@/settings/settings";
import type { Choice } from "./scenario";

const HATS: { key: Scenario; name: string; meta: string }[] = [
  { key: "free", name: "Free talk", meta: "Chat with Bolo about anything." },
  { key: "teacher", name: "English teacher", meta: "Bolo shows a better way to say it, then you try again." },
  { key: "standup", name: "Standup rehearsal", meta: "Bolo plays your manager and asks for your update." },
  { key: "ielts", name: "IELTS Speaking practice", meta: "An examiner asks Parts 1, 2 and 3. Practice only, no band score." },
];

/**
 * SCN-1, US-7.1 and Bolo's hats (DS v45 ScreenScenarioPicker / DesktopScenarioPicker): "What do you want to practise?"
 * One tap on a hat applies and closes (Standup once its difficulty is tapped). Talk length (5 / 10 min) saves without
 * closing; 10 is for Premium and Ultimate. Phone: a bottom sheet; desktop (≥ 900 px): a centred dialog.
 * ↑↓ moves between the hats, ←→ between chips, Esc closes.
 */
export function ScenarioPicker({ current, stammer, minutes, longTalks, onPick, onLength, onClose }: { current: Choice; stammer: boolean;
  minutes: 5 | 10; longTalks: boolean; onPick: (c: Choice) => void; onLength: (m: 5 | 10) => void; onClose: () => void }) {
  const [standup, setStandup] = useState(current.scenario === "standup");
  const box = useRef<HTMLDivElement>(null);
  useDialog(box, onClose);
  const pick = (d: Difficulty) => onPick({ scenario: "standup", difficulty: d });
  const cards = (e: KeyboardEvent) => {
    if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
    e.preventDefault();
    const all = Array.from(box.current?.querySelectorAll<HTMLElement>("[data-card]") ?? []);
    const at = all.findIndex((el) => el === document.activeElement);
    const next = all[(Math.max(0, at) + (e.key === "ArrowDown" ? 1 : all.length - 1)) % all.length];
    next?.focus();
    setStandup(next?.dataset.card === "standup");
  };
  const chips = (e: KeyboardEvent) => {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    e.preventDefault();
    box.current?.querySelector<HTMLElement>(e.key === "ArrowLeft" ? "[data-chip=friendly]" : "[data-chip=realistic]")?.focus();
  };
  const chosen = current.scenario === "standup" ? current.difficulty : "realistic";
  const on = (k: Scenario) => (k === "standup" ? standup : !standup && current.scenario === k);
  return (
    <>
      <div className="dk-dim" onClick={onClose} />
      <div className="bo-rate bo-scenario" ref={box} role="dialog" aria-modal="true" aria-labelledby="scenario-title">
        <div className="bo-sheet__grabber" aria-hidden="true" />
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 className="bo-sheet__title" id="scenario-title">What do you want to practise?</h2>
          <button className="bo-btn bo-btn--quiet-neutral" aria-label="Close" onClick={onClose}><Icon name="close" /></button>
        </div>
        <div className="bo-plans" role="radiogroup" aria-label="Scenario" style={{ marginTop: 16 }} onKeyDown={cards}>
          {HATS.map((h) => h.key === "standup" ? (
            <div key={h.key} className="bo-plan" role="radio" data-card="standup" aria-checked={on("standup")} tabIndex={0} autoFocus={on("standup")}
              style={{ flexWrap: "wrap", alignItems: "flex-start" }}
              onClick={() => setStandup(true)} onKeyDown={(e) => { if (e.key === "Enter" && e.target === e.currentTarget) pick(chosen); }}>
              <span className="bo-radio" style={{ marginTop: 2 }} /><span className="bo-plan__body"><span className="bo-plan__name">{h.name}</span>
                <span className="bo-plan__meta">{h.meta}</span></span>
              {standup && (
                <div className="bo-chips" style={{ width: "100%", paddingLeft: 34, marginTop: 8 }} role="group" aria-label="How real" onKeyDown={chips}>
                  {(["friendly", "realistic"] as const).map((d) => (
                    <button key={d} className="bo-chip" data-chip={d} aria-pressed={chosen === d}
                      onClick={(e) => { e.stopPropagation(); pick(d); }}>
                      {chosen === d && <Icon name="check" size="sm" />}{d === "friendly" ? "Friendly" : "Realistic"}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <button key={h.key} className="bo-plan" role="radio" data-card={h.key} aria-checked={on(h.key)} autoFocus={on(h.key)}
              onClick={() => onPick({ scenario: h.key, difficulty: current.difficulty })}>
              <span className="bo-radio" /><span className="bo-plan__body"><span className="bo-plan__name">{h.name}</span>
                <span className="bo-plan__meta">{h.meta}</span></span>
            </button>
          ))}
        </div>
        {standup && (
          <p className="bo-footnote" style={{ textAlign: "left", marginTop: 12 }}>
            {stammer ? "With I stammer on, Bolo won't cut in." : "Realistic interrupts once or twice, like a real meeting."}
          </p>
        )}
        <p className="bo-fix__label" style={{ margin: "16px 0 8px" }}>Talk length</p>
        <div className="bo-chips" role="group" aria-label="Talk length">
          {([5, 10] as const).map((m) => (
            <button key={m} className="bo-chip" aria-pressed={minutes === m} disabled={m === 10 && !longTalks} onClick={() => onLength(m)}>
              {minutes === m && <Icon name="check" size="sm" />}{m} min
            </button>
          ))}
        </div>
        {!longTalks && (
          <p className="bo-footnote" style={{ textAlign: "left", marginTop: 8 }}>
            10-minute talks come with Premium. <Link className="dk-link" href="/pricing">See plans</Link>
          </p>
        )}
      </div>
    </>
  );
}
