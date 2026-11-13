import { describe, expect, it } from "vitest";
import { MOODS, moodLabel, voiceKey, voiceSummary } from "./voices";

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
