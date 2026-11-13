import { describe, expect, it } from "vitest";
import { ANSWER_TIMES, MOODS, answerTimeSummary, moodLabel, voiceKey, voiceSummary } from "./voices";

describe("Bolo's voice and mood (P3c)", () => {
  it("names the voice the way the Settings row shows it", () => {
    expect(voiceSummary({ voiceAccent: "british", voiceGender: "female" })).toBe("British · Female");
    expect(voiceSummary({ voiceAccent: "indian", voiceGender: "male" })).toBe("Indian · Male");
    expect(voiceSummary({})).toBe("British · Female");
  });

  it("builds the server's voice key", () => {
    expect(voiceKey({ voiceAccent: "american", voiceGender: "male" })).toBe("american-male");
    expect(voiceKey({})).toBe("british-female");
  });

  it("has the four moods with the design system's words", () => {
    expect(MOODS.map((m) => m.value)).toEqual(["friendly", "funny", "professional", "calm"]);
    expect(MOODS[1]).toMatchObject({ label: "Funny", desc: "Playful, with light jokes. Laughs with you, never at you" });
    expect(MOODS[2].example).toBe("Good to see you, Rafi. How did the client demo go?");
    expect(moodLabel({})).toBe("Friendly");
    expect(moodLabel({ mood: "calm" })).toBe("Calm");
  });
});

describe("Time to answer (AX-7, changed 2026-11-14)", () => {
  it("names the choice the way the Settings row shows it", () => {
    expect(answerTimeSummary({ answerTime: "auto", answerTimeAuto: "relaxed" })).toBe("Automatic · Relaxed");
    expect(answerTimeSummary({ answerTime: "quick", answerTimeAuto: "relaxed" })).toBe("Quick");
    expect(answerTimeSummary({})).toBe("Automatic · Normal");
  });

  it("has the four choices with the design system's words", () => {
    expect(ANSWER_TIMES.map((a) => a.value)).toEqual(["auto", "quick", "normal", "relaxed"]);
    expect(ANSWER_TIMES[1]).toMatchObject({ label: "Quick", desc: "Bolo replies as soon as you stop. Help comes after 5 seconds of silence" });
    expect(ANSWER_TIMES[3].desc).toBe("Plenty of time to think. Help comes after 30 seconds");
  });
});
