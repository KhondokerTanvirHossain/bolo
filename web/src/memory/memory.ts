import { api } from "@/api/browser";

/** P3b (ACC-9, 2026-11-13): what Bolo remembers about the learner, as GET /api/memory answers it. Recent topics are never sent. */
export type FollowUpKind = "plan" | "worry" | "prep";
export type FollowUp = { id: string; kind: FollowUpKind; text: string; said: string };
export type Item = { id: string; text: string };
export type MemoryView = { enabled: boolean; followUps: FollowUp[]; about: Item[]; interests: Item[] };

export const KIND_LABEL: Record<FollowUpKind, string> = { plan: "Plan", worry: "On your mind", prep: "Your practice" };

/** "You said this on 11 Nov". The date is already a Bangladesh date; it is read as a calendar day, not shifted. */
export function saidLabel(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  const day = new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });
  return `You said this on ${day}`;
}

/** The Me row's sub-line. */
export function meRowLine(v: MemoryView | null): string {
  if (!v) return "What Bolo will ask you about";
  if (!v.enabled) return "Off";
  const n = v.followUps.length;
  if (n === 1) return "1 thing to follow up";
  if (n > 1) return `${n} things to follow up`;
  return v.about.length || v.interests.length ? "What Bolo will ask you about" : "Nothing yet";
}

export async function fetchMemory(): Promise<MemoryView | null> {
  const r = await api.get<MemoryView>("/api/memory");
  return r.kind === "ok" ? r.value : null;
}

export async function removeLine(id: string): Promise<boolean> {
  return (await api.del<void>(`/api/memory/lines/${encodeURIComponent(id)}`)).kind === "ok";
}

export async function setRemembering(enabled: boolean): Promise<boolean> {
  return (await api.put<void>("/api/memory", { enabled })).kind === "ok";
}

export async function forgetAll(): Promise<boolean> {
  return (await api.post<void>("/api/memory/forget")).kind === "ok";
}
