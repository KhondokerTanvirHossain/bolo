import { describe, expect, it, vi } from "vitest";
import { createPlayer, type PlayerContext } from "./player";

/** A stand-in AudioContext: a controllable clock, and sources whose end the test triggers. */
function fakeContext() {
  const sources: { start: number; duration: number; stopped: boolean; onended: (() => void) | null }[] = [];
  const ctx = {
    currentTime: 10,
    destination: {},
    createBuffer: (_ch: number, length: number, rate: number) => ({ duration: length / rate, copyToChannel: () => {} }),
    createBufferSource: () => {
      const s = { buffer: null as unknown as { duration: number }, start: 0, duration: 0, stopped: false, onended: null as (() => void) | null };
      sources.push(s as never);
      return {
        set buffer(b: { duration: number }) { s.buffer = b; s.duration = b.duration; },
        connect: () => {},
        start: (t: number) => { s.start = t; },
        stop: () => { s.stopped = true; },
        set onended(fn: (() => void) | null) { s.onended = fn; },
      };
    },
  };
  return { ctx: ctx as unknown as PlayerContext, sources, clock: ctx };
}

/** 100 ms of 24 kHz 16-bit PCM. */
const chunk = () => new ArrayBuffer(2400 * 2);

describe("player", () => {
  it("schedules buffers back to back", () => {
    const { ctx, sources } = fakeContext();
    const p = createPlayer(ctx);
    p.enqueue(chunk());
    p.enqueue(chunk());
    p.enqueue(chunk());
    const t = sources[0].start;
    expect(t).toBeGreaterThanOrEqual(10);
    expect(sources[1].start).toBeCloseTo(t + 0.1, 5);
    expect(sources[2].start).toBeCloseTo(t + 0.2, 5);
  });

  it("reports the start once per turn, with the delay until it is heard", () => {
    const { ctx } = fakeContext();
    const p = createPlayer(ctx);
    const onStart = vi.fn();
    p.onStart(onStart);
    p.enqueue(chunk());
    p.enqueue(chunk());
    expect(onStart).toHaveBeenCalledTimes(1);
    expect(onStart.mock.calls[0][0]).toBeGreaterThanOrEqual(0);
    p.newTurn();
    p.enqueue(chunk());
    expect(onStart).toHaveBeenCalledTimes(2);
  });

  it("says when everything queued has finished playing", () => {
    const { ctx, sources } = fakeContext();
    const p = createPlayer(ctx);
    const drained = vi.fn();
    p.onDrained(drained);
    p.enqueue(chunk());
    p.enqueue(chunk());
    expect(p.idle()).toBe(false);
    sources[0].onended?.();
    expect(drained).not.toHaveBeenCalled();
    sources[1].onended?.();
    expect(drained).toHaveBeenCalledTimes(1);
    expect(p.idle()).toBe(true);
  });

  it("stop() cancels what is queued", () => {
    const { ctx, sources } = fakeContext();
    const p = createPlayer(ctx);
    p.enqueue(chunk());
    p.enqueue(chunk());
    p.stop();
    expect(sources.every((s) => s.stopped)).toBe(true);
    expect(p.idle()).toBe(true);
  });
});
