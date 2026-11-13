import { describe, expect, it } from "vitest";
import { KIND_LABEL, meRowLine, saidLabel } from "./memory";

describe("What Bolo remembers (P3b)", () => {
  it("says when the learner said it, in Bangladesh dates", () => {
    expect(saidLabel("2026-11-11")).toBe("You said this on 11 Nov");
    expect(saidLabel("2026-01-02")).toBe("You said this on 2 Jan");
  });

  it("labels each kind of follow-up", () => {
    expect(KIND_LABEL).toEqual({ plan: "Plan", worry: "On your mind", prep: "Your practice" });
  });

  it("sums up the note for the Me row", () => {
    const empty = { enabled: true, followUps: [], about: [], interests: [] };
    expect(meRowLine(empty)).toBe("Nothing yet");
    expect(meRowLine({ ...empty, enabled: false })).toBe("Off");
    const one = { id: "a", kind: "plan" as const, text: "x", said: "2026-11-11" };
    expect(meRowLine({ ...empty, followUps: [one] })).toBe("1 thing to follow up");
    expect(meRowLine({ ...empty, followUps: [one, { ...one, id: "b" }, { ...one, id: "c" }] })).toBe("3 things to follow up");
    expect(meRowLine({ ...empty, about: [{ id: "x", text: "Lives in Mirpur" }] })).toBe("What Bolo will ask you about");
    expect(meRowLine(null)).toBe("What Bolo will ask you about");
  });
});
