/** Mic samples → 20 ms frames of 16 kHz PCM s16le (what the talk relay expects), and Bolo's 24 kHz PCM back. */

export const FRAME_SAMPLES = 320;

export type Downsampler = { ratio: number; pos: number; tail: Float32Array; out: number[] };

export function createDownsampler(inRate: number): Downsampler {
  return { ratio: inRate / 16000, pos: 0, tail: new Float32Array(0), out: [] };
}

/** Linear-interpolating resampler; leftovers carry to the next call, so blocks of any size lose nothing. */
export function downsampleTo16k(input: Float32Array, d: Downsampler): Int16Array[] {
  const buf = new Float32Array(d.tail.length + input.length);
  buf.set(d.tail);
  buf.set(input, d.tail.length);
  while (d.pos + 1 < buf.length) {
    const i = Math.floor(d.pos);
    const frac = d.pos - i;
    d.out.push(toInt16(buf[i] * (1 - frac) + buf[i + 1] * frac));
    d.pos += d.ratio;
  }
  const keep = Math.min(buf.length, Math.floor(d.pos));
  d.tail = buf.slice(keep);
  d.pos -= keep;
  const frames: Int16Array[] = [];
  while (d.out.length >= FRAME_SAMPLES) frames.push(Int16Array.from(d.out.splice(0, FRAME_SAMPLES)));
  return frames;
}

function toInt16(v: number): number {
  return Math.round(Math.max(-1, Math.min(1, v)) * 32767);
}

export function int16ToFloat32(buf: ArrayBuffer): Float32Array<ArrayBuffer> {
  const view = new DataView(buf);
  const out = new Float32Array(Math.floor(buf.byteLength / 2));
  for (let i = 0; i < out.length; i++) out[i] = view.getInt16(i * 2, true) / 32768;
  return out;
}
