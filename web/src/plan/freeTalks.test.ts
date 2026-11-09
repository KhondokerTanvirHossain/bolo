// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { counterLine, freeWeekOverNote, limitTitle, comeBackDay } from "./freeTalks";

describe("free talks (US-8.7)", () => {
  beforeEach(() => window.localStorage.clear());

  it("counterLineReadsLikeTheDesign", () => {
    expect(counterLine({ window: "first_week", left: 4, total: 7, resetsOn: "2026-11-16" })).toBe("4 of 7 free talks left in your first week");
    expect(counterLine({ window: "month", left: 1, total: 2, resetsOn: "2026-12-01" })).toBe("1 of 2 free talks left this month");
    expect(counterLine({ window: "month", left: 0, total: 2, resetsOn: "2026-12-01" })).toBe("Free talks come back on 1 December");
    expect(counterLine({ window: "first_week", left: 7, total: 7, resetsOn: null })).toBe("7 of 7 free talks left in your first week");
  });

  it("comeBackDayIsADayAndMonth", () => {
    expect(comeBackDay({ window: "month", left: 0, total: 2, resetsOn: "2027-01-01" })).toBe("1 January");
  });

  it("limitTitleNamesTheWindow", () => {
    expect(limitTitle({ window: "month", left: 0, total: 2, resetsOn: "2026-12-01" })).toBe("You've had this month's 2 free talks");
    expect(limitTitle({ window: "first_week", left: 0, total: 7, resetsOn: "2026-11-16" })).toBe("You've had your 7 free talks for this week");
  });

  it("freeWeekOverShowsOnce", () => {
    const month = { window: "month" as const, left: 2, total: 2, resetsOn: "2026-12-01" };
    expect(freeWeekOverNote({ window: "first_week", left: 3, total: 7, resetsOn: "2026-11-16" })).toBeNull();
    expect(freeWeekOverNote(month)).toBe("Your free week is over. From now on you get 2 free talks a month.");
    expect(freeWeekOverNote(month)).toBeNull();
  });
});
