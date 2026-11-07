import { describe, expect, it } from "vitest";
import { classifyMicError, unblockGuidance } from "./micErrors";

describe("classifyMicError", () => {
  it("treats a refused permission as blocked", () => {
    expect(classifyMicError("NotAllowedError")).toBe("blocked");
    expect(classifyMicError("SecurityError")).toBe("blocked");
  });
  it("treats a mic held by another app as busy, not blocked", () => {
    expect(classifyMicError("NotReadableError")).toBe("busy");
    expect(classifyMicError("AbortError")).toBe("busy");
  });
  it("treats a missing device as no-device", () => {
    expect(classifyMicError("NotFoundError")).toBe("no-device");
    expect(classifyMicError("OverconstrainedError")).toBe("no-device");
  });
  it("falls back to blocked for anything unknown", () => {
    expect(classifyMicError("WeirdError")).toBe("blocked");
  });
});

describe("unblockGuidance (US-1.3)", () => {
  const chrome = "Mozilla/5.0 (Macintosh) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36";
  const edge = chrome + " Edg/131.0";
  const firefox = "Mozilla/5.0 (Macintosh; rv:133.0) Gecko/20100101 Firefox/133.0";
  const safari = "Mozilla/5.0 (Macintosh) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15";

  it("names the right place in each browser", () => {
    expect(unblockGuidance(chrome)).toContain("Site settings");
    expect(unblockGuidance(edge)).toContain("Permissions for this site");
    expect(unblockGuidance(firefox)).toContain("crossed-out microphone");
    expect(unblockGuidance(safari)).toContain("Settings for this website");
  });
});
