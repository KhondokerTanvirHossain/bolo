import { describe, expect, it } from "vitest";
import { initialState, load, onboarding, reconcile, save, type OnboardingState } from "./steps";

function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    get length() { return data.size; },
    clear: () => data.clear(),
    getItem: (k) => data.get(k) ?? null,
    key: (i) => [...data.keys()][i] ?? null,
    removeItem: (k) => { data.delete(k); },
    setItem: (k, v) => { data.set(k, v); },
  };
}

describe("onboarding reducer", () => {
  it("keeps both consent choices disabled until 13+ is ticked", () => {
    const s = onboarding({ ...initialState, step: "consent" }, { type: "chooseConsent", keep: true });
    expect(s.step).toBe("consent");
    expect(s.keepRecordings).toBeNull();
  });

  it("moves to the mic once 13+ is ticked and a choice is made", () => {
    let s: OnboardingState = { ...initialState, step: "consent" };
    s = onboarding(s, { type: "toggleOver13" });
    s = onboarding(s, { type: "chooseConsent", keep: false });
    expect(s).toMatchObject({ step: "mic", over13: true, keepRecordings: false });
  });

  it("remembers the chosen microphone and the headphones answer", () => {
    let s: OnboardingState = { ...initialState, step: "mic" };
    s = onboarding(s, { type: "chooseMic", deviceId: "usb-1" });
    s = onboarding(s, { type: "setHeadphones", headphones: true });
    expect(s).toMatchObject({ micDeviceId: "usb-1", headphones: true });
  });
});

describe("resume (US-1.7)", () => {
  it("resumes at the stored step with earlier answers kept", () => {
    const storage = memoryStorage();
    save(storage, { ...initialState, step: "mic", over13: true, keepRecordings: true });
    expect(load(storage)).toMatchObject({ step: "mic", keepRecordings: true });
  });

  it("starts fresh when storage holds something unreadable", () => {
    const storage = memoryStorage();
    storage.setItem("bolo.onboarding.v1", "{not json");
    expect(load(storage)).toEqual(initialState);
  });

  it("starts fresh when storage itself throws (private windows)", () => {
    const throwing = { getItem: () => { throw new Error("blocked"); } } as unknown as Storage;
    expect(load(throwing)).toEqual(initialState);
  });

  it("sends a learner back to consent when the server has none", () => {
    const s = reconcile({ ...initialState, step: "mic", over13: true, keepRecordings: true }, false);
    expect(s.step).toBe("consent");
    expect(s.keepRecordings).toBeNull();
  });

  it("leaves the step alone when the server has the consent", () => {
    const s = reconcile({ ...initialState, step: "mic", over13: true, keepRecordings: true }, true);
    expect(s.step).toBe("mic");
  });
});
