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

/** A MediaRecorder stand-in that can stop on its own, like a real one whose track ended (headset unplugged). */
class FakeRecorder extends EventTarget {
  static isTypeSupported = () => true;
  state: "inactive" | "recording" = "inactive";
  mimeType = "audio/webm;codecs=opus";
  start() { this.state = "recording"; }
  stop() { this.state = "inactive"; this.dispatchEvent(new Event("stop")); }
  endOnItsOwn() {
    const data = new Event("dataavailable") as Event & { data: Blob };
    data.data = new Blob(["abc"]);
    this.dispatchEvent(data);
    this.stop();
  }
}

describe("startRecording", () => {
  it("still resolves stop() when the recorder already stopped by itself", async () => {
    const created: FakeRecorder[] = [];
    (globalThis as unknown as { MediaRecorder: unknown }).MediaRecorder = class extends FakeRecorder {
      constructor() { super(); created.push(this); }
    };
    const { startRecording } = await import("./recorder");
    const rec = startRecording({} as MediaStream);
    created[0]!.endOnItsOwn();
    const blob = await Promise.race([rec.stop(), new Promise<null>((r) => setTimeout(() => r(null), 200))]);
    expect(blob).not.toBeNull();
    expect(blob!.size).toBe(3);
  });
});
