"use client";
import { LEVEL_WORD, dayOf, monthOf, type Trends } from "./progress";

const SKILLS: [string, string][] = [["pronunciation", "Pronunciation"], ["fluency", "Fluency"], ["sentenceBuilding", "Sentences"], ["vocabulary", "Vocabulary"]];
const Y: Record<string, number> = { STRONG: 8, GOOD: 22, BUILDING: 36 };
const W = 200;
const H = 44;

/**
 * SkillTrends (PRG-1, US-6.2): 2×2, last 30 days, in words. One colour (the Coach's violet), no legend (each cell names
 * its skill), 2.5 px line, 8 px markers with a surface ring, a native hover tooltip per point and a hidden table.
 */
export function SkillTrends({ trends }: { trends: Trends }) {
  if (trends.sessions < trends.after) {
    return (
      <div className="bo-group bo-state" style={{ padding: "28px 20px" }}>
        <p className="bo-state__title" style={{ fontSize: 20, lineHeight: "26px" }}>Your trends appear after {trends.after} sessions</p>
        <p className="bo-row__sub" style={{ margin: 0 }}>{trends.sessions} of {trends.after}</p>
      </div>
    );
  }
  const now = Date.now();
  const from = now - 30 * 86_400_000;
  const x = (iso: string) => 6 + ((new Date(iso).getTime() - from) / (now - from)) * (W - 12);
  return (
    <div className="bo-trend">
      {SKILLS.map(([key, name]) => {
        const pts = trends.skills[key] ?? [];
        const last = pts.at(-1);
        return (
          <div className="bo-trend__cell" key={key}>
            <div className="bo-trend__name">{name}</div>
            <div className="bo-trend__level">{last ? LEVEL_WORD[last.level] ?? last.level : "Not yet"}</div>
            <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label={`${name} over the last 30 days`}>
              {/* Faint guides for Strong, Good and Building, so the line's height reads as a level. */}
              {[8, 22, 36].map((y) => <line key={y} x1={0} x2={W} y1={y} y2={y} stroke="var(--separator)" strokeWidth={1} vectorEffect="non-scaling-stroke" />)}
              {pts.length > 1 && <polyline className="line" vectorEffect="non-scaling-stroke" points={pts.map((p) => `${x(p.at)},${Y[p.level] ?? 22}`).join(" ")} />}
              {/* Markers as zero-length round-capped strokes: they stay round however the chart is stretched. */}
              {pts.map((p) => (
                <g key={p.at}>
                  <line x1={x(p.at)} x2={x(p.at)} y1={Y[p.level] ?? 22} y2={Y[p.level] ?? 22} stroke="var(--surface)" strokeWidth={12}
                    strokeLinecap="round" vectorEffect="non-scaling-stroke" />
                  <line x1={x(p.at)} x2={x(p.at)} y1={Y[p.level] ?? 22} y2={Y[p.level] ?? 22} stroke="var(--coach)" strokeWidth={8}
                    strokeLinecap="round" vectorEffect="non-scaling-stroke">
                    <title>{`${dayOf(p.at)} ${monthOf(p.at)} · ${LEVEL_WORD[p.level] ?? p.level}`}</title>
                  </line>
                </g>
              ))}
            </svg>
            <table style={{ position: "absolute", width: 1, height: 1, overflow: "hidden", clip: "rect(0 0 0 0)" }}>
              <caption>{name}</caption>
              <tbody>{pts.map((p) => <tr key={p.at}><td>{`${dayOf(p.at)} ${monthOf(p.at, "long")}`}</td><td>{LEVEL_WORD[p.level] ?? p.level}</td></tr>)}</tbody>
            </table>
          </div>
        );
      })}
    </div>
  );
}
