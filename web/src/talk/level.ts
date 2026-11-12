/** P3a (TLK-16 changed 2026-11-12): how loud the mic is right now, 0 (silence) to 1, for the level meter. */
export function levelOf(samples: Float32Array): number {
  if (samples.length === 0) return 0;
  let sum = 0;
  for (const v of samples) sum += v * v;
  const rms = Math.sqrt(sum / samples.length);
  // A square root lifts quiet speech so the bars move for normal voices; loud speech tops out at 1.
  return rms === 0 ? 0 : Math.min(1, Math.sqrt(rms) * 2);
}
