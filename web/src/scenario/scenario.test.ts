import { describe, expect, it } from "vitest";
import { scenarioLine, talkLabel, scenarioOf } from "./scenario";

describe("scenario lines (SCN-1, DS v44)", () => {
  it("Home's line names the scenario, the difficulty and the length", () => {
    expect(scenarioLine({ scenario: "free", difficulty: "realistic" }, 5)).toBe("Free talk · 5 min");
    expect(scenarioLine({ scenario: "standup", difficulty: "realistic" }, 5)).toBe("Standup rehearsal · Realistic · 5 min");
    expect(scenarioLine({ scenario: "standup", difficulty: "friendly" }, 10)).toBe("Standup rehearsal · Friendly · 10 min");
  });
  it("the talk's pill names only a standup", () => {
    expect(talkLabel({ scenario: "free", difficulty: null })).toBeNull();
    expect(talkLabel({ scenario: "standup", difficulty: "realistic" })).toBe("Standup · Realistic");
    expect(talkLabel({ scenario: "standup", difficulty: "friendly" })).toBe("Standup · Friendly");
  });
  it("missing settings mean Free talk, Realistic", () => {
    expect(scenarioOf(undefined)).toEqual({ scenario: "free", difficulty: "realistic" });
    expect(scenarioOf({ buddySpeed: "normal", captions: true, scenario: "standup" })).toEqual({ scenario: "standup", difficulty: "realistic" });
  });
});
