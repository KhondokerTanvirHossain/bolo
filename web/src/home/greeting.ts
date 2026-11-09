/** The dashboard Home's greeting (prototype "Good evening, Rafi"): by the clock, with the first name when signed in. */
export function greeting(hour: number, name?: string | null): string {
  const part = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  const first = name?.trim().split(/\s+/)[0];
  return first ? `${part}, ${first}` : part;
}

/**
 * Bolo's line on Home (founder, 2026-11-10): from the last talk's topic until real memory is discussed. A one-off note
 * ("See you tomorrow…") takes its place when there is one.
 */
export function boloLine(lastTopic: string | null | undefined, note?: string): string {
  if (note) return note;
  if (lastTopic) return `Last time: ${lastTopic}. Tell me what happened next.`;
  return "Bolo talks out loud, so turn your sound on.";
}
