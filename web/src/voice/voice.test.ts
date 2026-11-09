import { describe, expect, it } from "vitest";
import { daysUntil, summaryLine } from "./voice";
import type { Progress } from "@/progress/progress";

const progress = (skills: Record<string, string[]>, talks = 14, minutes = 70): Progress => ({
  effort: { talks, minutes },
  trends: { sessions: 5, after: 3, skills: Object.fromEntries(Object.entries(skills).map(([k, ls]) => [k, ls.map((level, i) => ({ at: `2026-11-0${i + 1}T10:00:00Z`, level }))])) },
  patterns: { active: [], fixed: [] }, latest: [],
});

describe("your voice, then and now (US-6.1)", () => {
  it("summaryNamesTheSkillThatRose", () => {
    expect(summaryLine(progress({ pronunciation: ["GOOD", "GOOD"], fluency: ["BUILDING", "GOOD"], vocabulary: ["BUILDING", "STRONG"] })))
      .toBe("Your fluency went from Building to Good.");
    expect(summaryLine(progress({ sentenceBuilding: ["GOOD", "STRONG"] }))).toBe("Your sentences went from Good to Strong.");
  });

  it("summaryFallsBackToEffort", () => {
    expect(summaryLine(progress({ fluency: ["GOOD", "BUILDING"] }))).toBe("14 talks and 70 minutes of speaking since Day 1.");
    expect(summaryLine(null)).toBe("");
    expect(summaryLine(progress({}, 1, 1))).toBe("1 talk and 1 minute of speaking since Day 1.");
  });

  it("betweenUnlocksShowsDaysLeft", () => {
    expect(daysUntil("2026-11-17", new Date("2026-11-14T10:00:00+06:00"))).toBe(3);
    expect(daysUntil("2026-11-17", new Date("2026-11-17T01:00:00+06:00"))).toBe(0);
  });
});
