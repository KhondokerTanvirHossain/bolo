import { fetchSettings, saveSettings, type Difficulty, type Scenario, type Settings } from "@/settings/settings";

/** SCN-1 (2026-11-15, DS v44 ScreenScenarioPicker): what the Talk button starts. */
export type Choice = { scenario: Scenario; difficulty: Difficulty };

export function scenarioOf(s: Settings | undefined | null): Choice {
  return { scenario: s?.scenario ?? "free", difficulty: s?.difficulty ?? "realistic" };
}

const DIFFICULTY: Record<Difficulty, string> = { friendly: "Friendly", realistic: "Realistic" };

/** Hats (2026-11-16, DS v45): each hat's name. */
export const HAT_NAME: Record<Scenario, string> = { free: "Free talk", teacher: "English teacher", standup: "Standup rehearsal", ielts: "IELTS practice" };

/** Home's line under the Talk button: "Free talk · 5 min", "Standup rehearsal · Realistic · 5 min", "English teacher · 10 min". */
export function scenarioLine(c: Choice, minutes: number): string {
  return c.scenario === "standup" ? `Standup rehearsal · ${DIFFICULTY[c.difficulty]} · ${minutes} min` : `${HAT_NAME[c.scenario] ?? "Free talk"} · ${minutes} min`;
}

/** 10-minute talks: Premium and Ultimate; learners with no plan (guests with sign-in off, unlimited) keep their choice. */
export function longTalks(plan: string | null | undefined): boolean {
  return plan == null || plan === "premium" || plan === "ultimate";
}

/** The talk's time pill names a standup ("Standup · Realistic"); a free talk shows only the time. */
export function talkLabel(t: { scenario?: string | null; difficulty?: string | null }): string | null {
  if (t.scenario === "teacher") return "English teacher";
  if (t.scenario === "ielts") return "IELTS practice";
  if (t.scenario !== "standup") return null;
  return `Standup · ${DIFFICULTY[(t.difficulty as Difficulty) ?? "realistic"] ?? "Realistic"}`;
}

/** Saves the choice (and the talk length, when given) with the learner's other settings (read fresh, so nothing else changes). */
export async function saveChoice(c: Choice, talkMinutes?: 5 | 10): Promise<Settings | null> {
  const current = await fetchSettings();
  if (!current) return null;
  const next = { ...current, scenario: c.scenario, difficulty: c.difficulty, ...(talkMinutes ? { talkMinutes } : {}) };
  return (await saveSettings(next)) ? next : null;
}
