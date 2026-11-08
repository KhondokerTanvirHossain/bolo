// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { SkillTrends } from "./SkillTrends";
import { SessionRow } from "./SessionRow";
import { PatternList } from "./PatternList";

afterEach(cleanup);
beforeEach(() => window.localStorage.clear());

const day = (d: number) => new Date(Date.UTC(2026, 10, d, 9)).toISOString();

describe("Skills (US-6.2)", () => {
  it("shows words, never numbers or percentages", () => {
    const pts = [{ at: day(1), level: "BUILDING" }, { at: day(4), level: "GOOD" }, { at: day(8), level: "GOOD" }];
    const { container } = render(<SkillTrends trends={{ sessions: 3, after: 3, skills:
      { pronunciation: pts, fluency: pts, sentenceBuilding: pts, vocabulary: pts } }} />);
    expect(screen.getAllByText("Good").length).toBeGreaterThanOrEqual(4);
    const visible = Array.from(container.querySelectorAll(".bo-trend__level")).map((e) => e.textContent ?? "");
    expect(visible.join(" ")).not.toMatch(/\d|%/);
  });

  it("waits for 3 talks, with the count", () => {
    render(<SkillTrends trends={{ sessions: 1, after: 3, skills: {} }} />);
    expect(screen.getByText("Your trends appear after 3 sessions")).toBeTruthy();
    expect(screen.getByText("1 of 3")).toBeTruthy();
  });
});

describe("History rows (SessionRow)", () => {
  it("reads as one sentence", () => {
    render(<SessionRow row={{ talkId: "t", startedAt: day(6), minutes: 5, topic: "The payment bug demo", status: "ready",
      fixes: ["The v sound", "Say it better"] }} onOpen={() => {}} />);
    expect(screen.getByRole("button", { name: "6 November, The payment bug demo, free talk, 5 minutes, 2 things to try" })).toBeTruthy();
  });
});

describe("My patterns (US-6.3)", () => {
  it("celebrates a newly fixed pattern once", () => {
    const patterns = { active: [{ key: "V_BH", title: "The v sound", tag: "IMPROVING" }], fixed: [{ key: "TH_TD", title: "The th sound", tag: "FIXED" }] };
    render(<PatternList patterns={patterns} onPractise={() => {}} />);
    expect(screen.getByText(/Fixed: you stopped/)).toBeTruthy();
    cleanup();
    render(<PatternList patterns={patterns} onPractise={() => {}} />);
    expect(screen.queryByText(/Fixed: you stopped/)).toBeNull();
    expect(screen.getByText("Improving")).toBeTruthy();
  });
});
