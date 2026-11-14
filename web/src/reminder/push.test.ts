import { describe, expect, it } from "vitest";
import { pushSupport } from "./push";

const IPHONE_SAFARI = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
const IPHONE_CHROME = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/124.0.6367.88 Mobile/15E148 Safari/604.1";
const IPHONE_OLD = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.3 Mobile/15E148 Safari/604.1";
const IPAD_DESKTOP = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15";
const ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
const MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

describe("pushSupport", () => {
  it("an iPhone browser tab must add Bolo to the Home Screen first", () => {
    expect(pushSupport({ ua: IPHONE_SAFARI, maxTouchPoints: 5, standalone: false, hasPush: false })).toBe("iphone-tab");
    expect(pushSupport({ ua: IPHONE_CHROME, maxTouchPoints: 5, standalone: false, hasPush: false })).toBe("iphone-tab");
  });

  it("Bolo opened from the iPhone Home Screen can get reminders", () => {
    expect(pushSupport({ ua: IPHONE_SAFARI, maxTouchPoints: 5, standalone: true, hasPush: true })).toBe("ok");
  });

  it("iOS before 16.4 can't get web reminders at all", () => {
    expect(pushSupport({ ua: IPHONE_OLD, maxTouchPoints: 5, standalone: false, hasPush: false })).toBe("old-iphone");
    expect(pushSupport({ ua: IPHONE_OLD, maxTouchPoints: 5, standalone: true, hasPush: false })).toBe("old-iphone");
  });

  it("an iPad that says it is a Mac counts as an iPhone tab", () => {
    expect(pushSupport({ ua: IPAD_DESKTOP, maxTouchPoints: 5, standalone: false, hasPush: false })).toBe("iphone-tab");
  });

  it("Android and computers just work", () => {
    expect(pushSupport({ ua: ANDROID, maxTouchPoints: 5, standalone: false, hasPush: true })).toBe("ok");
    expect(pushSupport({ ua: MAC, maxTouchPoints: 0, standalone: false, hasPush: true })).toBe("ok");
  });

  it("a browser without push can't", () => {
    expect(pushSupport({ ua: MAC, maxTouchPoints: 0, standalone: false, hasPush: false })).toBe("unsupported");
  });
});
