import { api } from "@/api/browser";

export type HomeCard =
  | { kind: "feedback"; talkId: string }
  | { kind: "practice"; pattern: string; title: string; talkId?: string }
  | { kind: "intro"; day: number }
  | { kind: "reminder" };
import type { FreeTalks } from "@/plan/freeTalks";

export type WeekDay = { date: string; state: "done" | "rest" | "missed" | "today" | "future" };
export type Week = { run: number; restUsed: boolean; welcomeBack: boolean; days: WeekDay[] };
export type Home = { primary: "talk"; card: HomeCard | null; freeTalks?: FreeTalks | null; lastTopic?: string | null; week?: Week | null };

/** GET /api/home (US-6.9). null when it can't load: Home then shows just the Talk button. */
export async function fetchHome(): Promise<Home | null> {
  const res = await api.get<Home>("/api/home");
  return res.kind === "ok" ? res.value : null;
}
