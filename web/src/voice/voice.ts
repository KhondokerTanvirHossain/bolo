import { api } from "@/api/browser";
import type { Progress } from "@/progress/progress";

/** GET /api/me/voice (PRG-2, US-6.1). Dates are Bangladesh dates (YYYY-MM-DD). */
export type IntroRecording = { day: number; recordedAt: string; onServer: boolean };
export type Voice = { day1: string; unlocked: number[]; due: number | null; nextDay: number; nextOn: string; recordings: IntroRecording[] };

export async function fetchVoice(): Promise<Voice | null> {
  const r = await api.get<Voice>("/api/me/voice");
  return r.kind === "ok" ? r.value : null;
}

/**
 * POST /api/me/intro/{day}: the audio goes only for a learner who keeps recordings; otherwise only the date is noted and
 * the clip stays in this browser.
 */
export async function recordIntro(day: number, clip: Blob | null): Promise<"ok" | "not-kept" | "failed"> {
  const form = new FormData();
  if (clip) form.append("audio", clip, `intro-${day}.webm`);
  else form.append("kept", "device");
  try {
    const res = await fetch(`/api/me/intro/${day}`, { method: "POST", credentials: "same-origin", headers: { "X-Bolo": "1" }, body: form });
    if (res.ok) return "ok";
    if (res.status === 422 && ((await res.json().catch(() => ({}))) as { code?: string }).code === "not-kept") return "not-kept";
    return "failed";
  } catch {
    return "failed";
  }
}

/** The local key of a learner's Day N intro: per learner, so a shared browser never plays someone else's voice. */
export const introKey = (learnerId: string, day: number) => `intro-${learnerId}-${day}`;

const RANK: Record<string, number> = { BUILDING: 1, GOOD: 2, STRONG: 3 };
const WORD: Record<string, string> = { BUILDING: "Building", GOOD: "Good", STRONG: "Strong" };
const SKILLS: [string, string][] = [["pronunciation", "pronunciation"], ["fluency", "fluency"], ["sentenceBuilding", "sentences"], ["vocabulary", "vocabulary"]];

/**
 * The one line under the two clips (US-6.1): the first skill that rose from the first to the latest talk, else the
 * effort since Day 1. Never "you didn't improve".
 */
export function summaryLine(p: Progress | null): string {
  if (!p) return "";
  for (const [key, name] of SKILLS) {
    const pts = p.trends.skills[key] ?? [];
    if (pts.length < 2) continue;
    const first = String(pts[0].level), last = String(pts[pts.length - 1].level);
    if ((RANK[last] ?? 0) > (RANK[first] ?? 0)) return `Your ${name} went from ${WORD[first]} to ${WORD[last]}.`;
  }
  const { talks, minutes } = p.effort;
  return `${talks} ${talks === 1 ? "talk" : "talks"} and ${minutes} ${minutes === 1 ? "minute" : "minutes"} of speaking since Day 1.`;
}

/** Whole days from today (in Bangladesh) to a Bangladesh date; 0 on the day itself. */
export function daysUntil(date: string, now: Date = new Date()): number {
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Dhaka" }).format(now);
  const ms = Date.parse(`${date}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`);
  return Math.max(0, Math.round(ms / 86_400_000));
}
