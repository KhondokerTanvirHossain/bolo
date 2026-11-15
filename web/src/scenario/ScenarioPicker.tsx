"use client";
import { useRef, useState, type KeyboardEvent } from "react";
import { Icon } from "@/scene/Scene";
import { useDialog } from "@/ui/useDialog";
import type { Difficulty } from "@/settings/settings";
import type { Choice } from "./scenario";

/**
 * SCN-1, US-7.1 (DS v44 ScreenScenarioPicker / DesktopScenarioPicker): "What do you want to practise?" Free talk or a
 * Standup rehearsal. One tap applies and closes: Free talk at once; Standup once its difficulty is tapped. Phone: a
 * bottom sheet; desktop (≥ 900 px): a centred dialog. ↑↓ moves between the cards, ←→ between the chips, Esc closes.
 */
export function ScenarioPicker({ current, stammer, onPick, onClose }: { current: Choice; stammer: boolean;
  onPick: (c: Choice) => void; onClose: () => void }) {
  const [standup, setStandup] = useState(current.scenario === "standup");
  const box = useRef<HTMLDivElement>(null);
  useDialog(box, onClose);
  const pick = (d: Difficulty) => onPick({ scenario: "standup", difficulty: d });
  const cards = (e: KeyboardEvent) => {
    if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
    e.preventDefault();
    const down = e.key === "ArrowDown";
    setStandup(down);
    box.current?.querySelector<HTMLElement>(down ? "[data-card=standup]" : "[data-card=free]")?.focus();
  };
  const chips = (e: KeyboardEvent) => {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    e.preventDefault();
    box.current?.querySelector<HTMLElement>(e.key === "ArrowLeft" ? "[data-chip=friendly]" : "[data-chip=realistic]")?.focus();
  };
  const chosen = current.scenario === "standup" ? current.difficulty : "realistic";
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
          <button className="bo-plan" role="radio" data-card="free" aria-checked={!standup} autoFocus={!standup}
            onClick={() => onPick({ scenario: "free", difficulty: current.difficulty })}>
            <span className="bo-radio" /><span className="bo-plan__body"><span className="bo-plan__name">Free talk</span>
              <span className="bo-plan__meta">Chat about your day, your work, anything.</span></span>
          </button>
          <div className="bo-plan" role="radio" data-card="standup" aria-checked={standup} tabIndex={0} autoFocus={standup}
            style={{ flexWrap: "wrap", alignItems: "flex-start" }}
            onClick={() => setStandup(true)} onKeyDown={(e) => { if (e.key === "Enter" && e.target === e.currentTarget) pick(chosen); }}>
            <span className="bo-radio" style={{ marginTop: 2 }} /><span className="bo-plan__body"><span className="bo-plan__name">Standup rehearsal</span>
              <span className="bo-plan__meta">Bolo plays your manager and asks for your update.</span></span>
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
        </div>
        <p className="bo-footnote" style={{ textAlign: "left", marginTop: 12 }}>
          {stammer ? "With I stammer on, Bolo won't cut in." : "Realistic interrupts once or twice, like a real meeting."}
        </p>
      </div>
    </>
  );
}
