/** US-1.6 rules for the Day 1 clip. */
export const DAY1 = { maxSeconds: 30, shortSeconds: 10, maxRecordings: 4, savedToastMs: 1800 } as const;

/** Under 10 s: ask once "Want to say a bit more?"; otherwise save. */
export function afterRecording(durationS: number, alreadyPrompted: boolean): "short-prompt" | "saved" {
  return durationS < DAY1.shortSeconds && !alreadyPrompted ? "short-prompt" : "saved";
}

/** The first recording plus up to 3 re-records. */
export function canRecordAgain(recordingsSoFar: number): boolean {
  return recordingsSoFar < DAY1.maxRecordings;
}
