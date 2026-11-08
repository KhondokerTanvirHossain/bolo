import { api } from "@/api/browser";

export type HomeCard =
  | { kind: "feedback"; talkId: string }
  | { kind: "practice"; pattern: string; title: string };
export type Home = { primary: "talk"; card: HomeCard | null };

/** GET /api/home (US-6.9). null when it can't load: Home then shows just the Talk button. */
export async function fetchHome(): Promise<Home | null> {
  const res = await api.get<Home>("/api/home");
  return res.kind === "ok" ? res.value : null;
}
