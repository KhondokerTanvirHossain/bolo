import { describe, expect, it } from "vitest";
import { pickMimeType } from "./recorder";

describe("pickMimeType", () => {
  it("prefers Opus in WebM (Chrome, Edge, Firefox)", () => {
    expect(pickMimeType(() => true)).toBe("audio/webm;codecs=opus");
  });

  it("falls back to MP4 where WebM can't be recorded (Safari)", () => {
    expect(pickMimeType((t) => t === "audio/mp4")).toBe("audio/mp4");
  });

  it("lets the browser choose when neither is supported", () => {
    expect(pickMimeType(() => false)).toBe("");
  });
});
