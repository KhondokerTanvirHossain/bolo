import { describe, expect, it } from "vitest";
import { classifyMicCheck } from "./micCheck";

/** Builds a level history: `quietCount` samples at the room floor, then `voiceCount` samples of speech. */
function history(floorDb: number, voiceDb: number, quietCount = 30, voiceCount = 20): number[] {
  return [...Array(quietCount).fill(floorDb), ...Array(voiceCount).fill(voiceDb)];
}

describe("classifyMicCheck", () => {
  it("calls a clear voice in a quiet room great", () => {
    expect(classifyMicCheck(history(-60, -25))).toBe("great");
  });

  it("calls a voice barely above the room noisy", () => {
    expect(classifyMicCheck(history(-35, -25))).toBe("noisy");
  });

  it("calls a faint voice quiet", () => {
    expect(classifyMicCheck(history(-60, -45))).toBe("quiet");
  });

  it("waits while nobody has spoken yet", () => {
    expect(classifyMicCheck(history(-70, -70))).toBe("idle");
  });

  it("waits until there are enough samples to judge", () => {
    expect(classifyMicCheck([-25, -25, -60])).toBe("idle");
  });
});
