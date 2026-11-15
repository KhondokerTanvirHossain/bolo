import { fetchSettings, saveSettings, type Difficulty, type Scenario, type Settings } from "@/settings/settings";

/** SCN-1 (2026-11-15, DS v44 ScreenScenarioPicker): what the Talk button starts. */
export type Choice = { scenario: Scenario; difficulty: Difficulty };

export function scenarioOf(s: Settings | undefined | null): Choice {
  return { scenario: s?.scenario ?? "free", difficulty: s?.difficulty ?? "realistic" };
}

const DIFFICULTY: Record<Difficulty, string> = { friendly: "Friendly", realistic: "Realistic" };

/** Home's line under the Talk button: "Free talk · 5 min", "Standup rehearsal · Realistic · 5 min". */
export function scenarioLine(c: Choice, minutes: number): string {
  return c.scenario === "standup" ? `Standup rehearsal · ${DIFFICULTY[c.difficulty]} · ${minutes} min` : `Free talk · ${minutes} min`;
}

/** The talk's time pill names a standup ("Standup · Realistic"); a free talk shows only the time. */
export function talkLabel(t: { scenario?: string | null; difficulty?: string | null }): string | null {
  if (t.scenario !== "standup") return null;
  return `Standup · ${DIFFICULTY[(t.difficulty as Difficulty) ?? "realistic"] ?? "Realistic"}`;
}

/** Saves the choice with the learner's other settings (read fresh, so nothing else changes). */
export async function saveChoice(c: Choice): Promise<Settings | null> {
  const current = await fetchSettings();
  if (!current) return null;
  const next = { ...current, scenario: c.scenario, difficulty: c.difficulty };
  return (await saveSettings(next)) ? next : null;
}
