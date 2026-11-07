import { int16ToFloat32 } from "./pcm";

export type PlayerContext = Pick<AudioContext, "currentTime" | "destination" | "createBuffer" | "createBufferSource">;

export type Player = {
  enqueue(pcm24k: ArrayBuffer): void;
  /** Called once per turn when its first audio is scheduled, with the milliseconds until it is heard. */
  onStart(cb: (delayMs: number) => void): void;
  /** Called when everything queued has finished playing. */
  onDrained(cb: () => void): void;
  newTurn(): void;
  idle(): boolean;
  stop(): void;
};

const RATE = 24000;
const LEAD_S = 0.03;

/** Plays Bolo's 24 kHz PCM chunks back to back, gap-free, on the Web Audio clock. */
export function createPlayer(ctx: PlayerContext): Player {
  const active = new Set<AudioBufferSourceNode>();
  let next = 0;
  let started = false;
  let startCb: ((delayMs: number) => void) | null = null;
  let drainCb: (() => void) | null = null;

  return {
    enqueue(pcm) {
      const samples = int16ToFloat32(pcm);
      if (samples.length === 0) return;
      const buffer = ctx.createBuffer(1, samples.length, RATE);
      buffer.copyToChannel(samples, 0);
      const source = ctx.createBufferSource();
      source.buffer = buffer;
      source.connect(ctx.destination);
      const at = Math.max(ctx.currentTime + LEAD_S, next);
      source.start(at);
      next = at + buffer.duration;
      active.add(source);
      source.onended = () => {
        if (!active.delete(source)) return;
        if (active.size === 0) drainCb?.();
      };
      if (!started) {
        started = true;
        startCb?.((at - ctx.currentTime) * 1000);
      }
    },
    onStart(cb) { startCb = cb; },
    onDrained(cb) { drainCb = cb; },
    newTurn() { started = false; },
    idle() { return active.size === 0; },
    stop() {
      for (const s of active) {
        try { s.stop(); } catch { /* already stopped */ }
      }
      active.clear();
      next = 0;
    },
  };
}
