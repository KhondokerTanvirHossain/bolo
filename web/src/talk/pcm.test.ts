import { describe, expect, it } from "vitest";
import { createDownsampler, downsampleTo16k, int16ToFloat32 } from "./pcm";

describe("downsampleTo16k", () => {
  it("turns 960 samples at 48 kHz into one 20 ms frame of 320 samples", () => {
    const frames = downsampleTo16k(new Float32Array(960).fill(0.5), createDownsampler(48000));
    expect(frames).toHaveLength(1);
    expect(frames[0]).toHaveLength(320);
    expect(frames[0][100]).toBe(Math.round(0.5 * 32767));
  });

  it("carries leftovers across calls so no samples are lost", () => {
    const d = createDownsampler(48000);
    const frames = [
      ...downsampleTo16k(new Float32Array(500).fill(0.1), d),
      ...downsampleTo16k(new Float32Array(500).fill(0.1), d),
      ...downsampleTo16k(new Float32Array(920).fill(0.1), d),
    ];
    expect(frames).toHaveLength(2);
  });

  it("handles 44.1 kHz input", () => {
    const frames = downsampleTo16k(new Float32Array(44100).fill(0.2), createDownsampler(44100));
    expect(frames.length).toBeGreaterThanOrEqual(49);
    expect(frames.length).toBeLessThanOrEqual(50);
  });

  it("clips at full scale", () => {
    const [frame] = downsampleTo16k(new Float32Array(960).fill(1.7), createDownsampler(48000));
    expect(frame[5]).toBe(32767);
    const [neg] = downsampleTo16k(new Float32Array(960).fill(-1.7), createDownsampler(48000));
    expect(neg[5]).toBe(-32767);
  });
});

describe("int16ToFloat32", () => {
  it("maps little-endian 16-bit samples to -1..1", () => {
    const buf = new ArrayBuffer(4);
    const view = new DataView(buf);
    view.setInt16(0, 16384, true);
    view.setInt16(2, -32768, true);
    const out = int16ToFloat32(buf);
    expect(out[0]).toBeCloseTo(0.5, 3);
    expect(out[1]).toBeCloseTo(-1, 3);
  });
});
