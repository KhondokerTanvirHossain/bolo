// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

const stopTrack = vi.fn();
vi.mock("./openMic", () => ({
  openMic: vi.fn(async () => ({ stream: { getTracks: () => [{ stop: stopTrack }] } })),
}));

import { startPcmRecording, trimToSamples } from "./recordPcm";

describe("trimToSamples", () => {
  it("keeps the frames under the cap whole", () => {
    const frames = [new Int16Array(4), new Int16Array(4)];
    expect(trimToSamples(frames, 10).reduce((n, f) => n + f.length, 0)).toBe(8);
  });
  /** Review: the stop timer can fire late; the WAV must never exceed the server's 15 s. */
  it("cuts the frame that crosses the cap", () => {
    const frames = [new Int16Array(6), new Int16Array(6), new Int16Array(6)];
    const out = trimToSamples(frames, 10);
    expect(out.reduce((n, f) => n + f.length, 0)).toBe(10);
    expect(out).toHaveLength(2);
  });
});

describe("startPcmRecording", () => {
  afterEach(() => { vi.unstubAllGlobals(); stopTrack.mockClear(); });

  /** Review: a mic opened before the worklet fails to load must be released, or the browser's mic light stays on. */
  it("releases the mic when the worklet fails to load", async () => {
    vi.stubGlobal("AudioContext", class {
      sampleRate = 48000;
      audioWorklet = { addModule: () => Promise.reject(new Error("no worklet")) };
      resume() { return Promise.resolve(); }
      close() { return Promise.resolve(); }
    });
    await expect(startPcmRecording(null, 1000)).rejects.toThrow("no worklet");
    expect(stopTrack).toHaveBeenCalled();
  });

  it("releases the mic when the worklet node can't be made", async () => {
    vi.stubGlobal("AudioContext", class {
      sampleRate = 48000;
      audioWorklet = { addModule: () => Promise.resolve() };
      resume() { return Promise.resolve(); }
      close() { return Promise.resolve(); }
      createMediaStreamSource() { return { connect() {} }; }
    });
    vi.stubGlobal("AudioWorkletNode", class { constructor() { throw new Error("no node"); } });
    await expect(startPcmRecording(null, 1000)).rejects.toThrow("no node");
    expect(stopTrack).toHaveBeenCalled();
  });
});
