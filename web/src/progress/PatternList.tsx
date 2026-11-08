"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import type { Patterns } from "./progress";

const TAG: Record<string, [string, string]> = { NEW: ["New", "bo-tag--new"], IMPROVING: ["Improving", "bo-tag--improving"], STEADY: ["Steady", "bo-tag--steady"] };
const SOUNDS = new Set(["V_BH", "W_V", "TH_TD", "Z_J", "S_SH", "S_CLUSTER_VOWEL"]);
const SEEN_KEY = "bolo.fixedSeen";

function seenFixed(): string[] {
  try {
    return JSON.parse(window.localStorage.getItem(SEEN_KEY) ?? "[]") as string[];
  } catch {
    return [];
  }
}

/** My patterns (PRG-3, US-6.3): up to 5, tagged; a sound opens practice on it. "Fixed" gets a small note the first time. */
export function PatternList({ patterns, onPractise }: { patterns: Patterns; onPractise: (key: string) => void }) {
  const [note, setNote] = useState<string | null>(null);
  useEffect(() => {
    const seen = seenFixed();
    const fresh = patterns.fixed.find((p) => !seen.includes(p.key));
    if (fresh) setNote(`Fixed: you stopped ${fresh.title.charAt(0).toLowerCase() + fresh.title.slice(1)} slipping. Nice work.`);
    try {
      window.localStorage.setItem(SEEN_KEY, JSON.stringify([...new Set([...seen, ...patterns.fixed.map((p) => p.key)])]));
    } catch {
      // a per-browser nicety
    }
  }, [patterns.fixed]);

  if (patterns.active.length === 0 && patterns.fixed.length === 0) {
    return <div className="bo-group" style={{ padding: "16px 20px" }}><p className="bo-row__sub" style={{ margin: 0 }}>Your patterns appear after your first feedback.</p></div>;
  }
  return (
    <>
      {note && <p className="bo-tag bo-tag--improving" role="status" style={{ height: "auto", padding: "8px 12px", marginBottom: 12, fontSize: 14 }}>{note}</p>}
      <div className="bo-group">
        {patterns.active.map((p) => {
          const [word, cls] = TAG[p.tag] ?? [p.tag, "bo-tag--steady"];
          const practisable = SOUNDS.has(p.key);
          const body = (
            <>
              <span className="bo-row__body"><span className="bo-row__title" style={{ display: "block" }}>{p.title}</span>
                {practisable && <span className="bo-row__sub">Tap to practise</span>}</span>
              <span className={`bo-tag ${cls}`}>{word}</span>
              {practisable && <Icon name="chevron_right" />}
            </>
          );
          return practisable
            ? <button key={p.key} className="bo-row" onClick={() => onPractise(p.key)}
                style={{ width: "100%", border: 0, background: "transparent", textAlign: "left", color: "inherit", font: "inherit" }}>{body}</button>
            : <div key={p.key} className="bo-row">{body}</div>;
        })}
      </div>
      {patterns.fixed.length > 0 && (
        <>
          <h3 className="bo-week-label">Fixed</h3>
          <div className="bo-group">
            {patterns.fixed.map((p) => (
              <div className="bo-row" key={p.key}><span className="bo-row__body"><span className="bo-row__title">{p.title}</span></span>
                <Icon name="check_circle" /></div>
            ))}
          </div>
        </>
      )}
    </>
  );
}
