import { api } from "@/api/browser";

export type Level = "BUILDING" | "GOOD" | "STRONG";
export type TrendPoint = { at: string; level: Level | string };
export type Trends = { sessions: number; after: number; skills: Partial<Record<string, TrendPoint[]>> };
export type PatternTag = { key: string; title: string; tag: "NEW" | "IMPROVING" | "STEADY" | "FIXED" | string };
export type Patterns = { active: PatternTag[]; fixed: PatternTag[] };
export type Row = { talkId: string; startedAt: string; minutes: number; topic: string; status: string; fixes: string[] };
export type Progress = { effort: { talks: number; minutes: number }; trends: Trends; patterns: Patterns; latest: Row[] };

export async function fetchProgress(): Promise<Progress | null> {
  const r = await api.get<Progress>("/api/progress");
  return r.kind === "ok" ? r.value : null;
}

export async function fetchHistory(): Promise<Row[] | null> {
  const r = await api.get<{ sessions: Row[] }>("/api/history");
  return r.kind === "ok" ? r.value.sessions : null;
}

/** US-8.6: the talk's audio goes at once; the feedback stays. */
export async function deleteRecording(talkId: string): Promise<boolean> {
  return (await api.post<void>(`/api/talk/${talkId}/recording/delete`)).kind === "ok";
}

export const LEVEL_WORD: Record<string, string> = { BUILDING: "Building", GOOD: "Good", STRONG: "Strong" };
const ZONE = "Asia/Dhaka";
export const dayOf = (iso: string) => new Intl.DateTimeFormat("en-GB", { day: "numeric", timeZone: ZONE }).format(new Date(iso));
export const monthOf = (iso: string, style: "short" | "long" = "short") =>
  new Intl.DateTimeFormat("en-GB", { month: style, timeZone: ZONE }).format(new Date(iso));
