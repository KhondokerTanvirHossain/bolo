/** US-1.6 rules for the Day 1 clip. */
import type { Recording } from "@/audio/recorder";

/** savedMs: how long the "Saved" state (with "Record again") shows before the first talk opens. */
export const DAY1 = { maxSeconds: 30, shortSeconds: 10, maxRecordings: 4, savedMs: 4000 } as const;

/** Under 10 s: ask once "Want to say a bit more?"; otherwise save. */
export function afterRecording(durationS: number, alreadyPrompted: boolean): "short-prompt" | "saved" {
  return durationS < DAY1.shortSeconds && !alreadyPrompted ? "short-prompt" : "saved";
}

/** The first recording plus up to 3 re-records. */
export function canRecordAgain(recordingsSoFar: number): boolean {
  return recordingsSoFar < DAY1.maxRecordings;
}

/** Opens the mic and starts recording; if the recorder can't start, the mic is released before the error surfaces. */
export async function startClip(
  open: () => Promise<MediaStream>,
  start: (stream: MediaStream) => Recording,
): Promise<{ stream: MediaStream; recording: Recording }> {
  const stream = await open();
  try {
    return { stream, recording: start(stream) };
  } catch (e) {
    stream.getTracks().forEach((t) => t.stop());
    throw e;
  }
}
