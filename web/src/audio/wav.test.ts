import { describe, expect, it } from "vitest";
import { toWav } from "./wav";

describe("toWav", () => {
  it("writes a 16 kHz mono 16-bit WAV header followed by the samples", () => {
    const buf = toWav([Int16Array.from([1, -1, 300]), Int16Array.from([7])], 16000);
    const v = new DataView(buf);
    const text = (o: number) => String.fromCharCode(v.getUint8(o), v.getUint8(o + 1), v.getUint8(o + 2), v.getUint8(o + 3));
    expect(text(0)).toBe("RIFF");
    expect(text(8)).toBe("WAVE");
    expect(v.getUint16(20, true)).toBe(1);
    expect(v.getUint16(22, true)).toBe(1);
    expect(v.getUint32(24, true)).toBe(16000);
    expect(v.getUint16(34, true)).toBe(16);
    expect(text(36)).toBe("data");
    expect(v.getUint32(40, true)).toBe(8);
    expect(v.getInt16(44 + 4, true)).toBe(300);
    expect(buf.byteLength).toBe(52);
  });
});
