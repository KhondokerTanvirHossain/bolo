/** US-1.5 mic check, judged from a rolling history of RMS levels in dBFS (about 10 per second). */
export type MicCheckResult = "idle" | "great" | "noisy" | "quiet";

export const MIC_CHECK = {
  /** Need about 2 s of history before judging. */
  minSamples: 20,
  /** Below this the learner hasn't spoken yet. */
  silenceDb: -50,
  /** A voice peak under this is too faint to score well. */
  quietDb: -38,
  /** The voice must stand this far above the room. */
  minSnrDb: 15,
} as const;

function percentile(sorted: number[], p: number): number {
  const i = Math.min(sorted.length - 1, Math.max(0, Math.round((p / 100) * (sorted.length - 1))));
  return sorted[i] ?? -Infinity;
}

export function classifyMicCheck(levelsDb: number[]): MicCheckResult {
  if (levelsDb.length < MIC_CHECK.minSamples) return "idle";
  const sorted = [...levelsDb].sort((a, b) => a - b);
  const floor = percentile(sorted, 20);
  const voice = percentile(sorted, 90);
  if (voice < MIC_CHECK.silenceDb) return "idle";
  if (voice < MIC_CHECK.quietDb) return "quiet";
  if (voice - floor < MIC_CHECK.minSnrDb) return "noisy";
  return "great";
}

/** Once the learner has spoken, keep the verdict when they fall silent; new speech can replace it. */
export function stickyVerdict(previous: MicCheckResult, next: MicCheckResult): MicCheckResult {
  return next === "idle" ? previous : next;
}
