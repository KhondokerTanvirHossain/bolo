import { describe, expect, it } from "vitest";
import { afterRecording, canRecordAgain, DAY1 } from "./day1";

describe("Day 1 clip rules (US-1.6)", () => {
  it("records for 30 seconds at most", () => {
    expect(DAY1.maxSeconds).toBe(30);
  });

  it("asks once for a bit more when the clip is under 10 s", () => {
    expect(afterRecording(6, false)).toBe("short-prompt");
  });

  it("doesn't ask a second time", () => {
    expect(afterRecording(6, true)).toBe("saved");
  });

  it("saves a clip of 10 s or more straight away", () => {
    expect(afterRecording(25, false)).toBe("saved");
    expect(afterRecording(10, false)).toBe("saved");
  });

  it("allows up to 3 re-records (4 recordings in all)", () => {
    expect(canRecordAgain(1)).toBe(true);
    expect(canRecordAgain(3)).toBe(true);
    expect(canRecordAgain(4)).toBe(false);
  });
});

describe("startClip", () => {
  it("releases the mic when the recorder can't start", async () => {
    const { startClip } = await import("./day1");
    let stopped = 0;
    const stream = { getTracks: () => [{ stop: () => { stopped++; } }] } as unknown as MediaStream;
    await expect(startClip(async () => stream, () => { throw new Error("NotSupportedError"); })).rejects.toThrow();
    expect(stopped).toBe(1);
  });
});
