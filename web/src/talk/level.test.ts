import { describe, expect, it } from "vitest";
import { levelOf } from "./level";

const sine = (amp: number, n = 2048) => Float32Array.from({ length: n }, (_, i) => amp * Math.sin(i / 8));

/** P3a: the level meter (TLK-16 changed 2026-11-12) moves with the learner's voice. */
describe("levelOf", () => {
  it("silenceIsZero", () => expect(levelOf(new Float32Array(1024))).toBe(0));
  it("aLoudToneIsNearOne", () => expect(levelOf(sine(0.5))).toBeGreaterThanOrEqual(0.8));
  it("aQuietToneIsLow", () => expect(levelOf(sine(0.01))).toBeLessThan(0.3));
});
