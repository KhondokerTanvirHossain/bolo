import { api } from "@/api/browser";

/** The founder's admin portal API (P2a). Anyone else gets "not found". */
export type AdminUser = { id: string; email: string; name: string | null; kind: string; unlimited: boolean; plan: string;
  planEndsAt: string | null; talksThisMonth: number; lastTalkAt: string | null };
export type Day = { date: string; talks: number; minutes: number; newAccounts: number; costUsd: number };
export type Usage = { days: Day[]; now: { live: number; today: number; cap: number } };
export type Plan = { plan: string; monthlyTalks: number; priceBdt: number; priceUsd: number };
export type Settings = { dailyTalkCap: number; plans: Plan[] };

export async function fetchSettings(): Promise<Settings | null> {
  const r = await api.get<Settings>("/api/admin/settings");
  return r.kind === "ok" ? r.value : null;
}
export async function saveSettings(s: Settings): Promise<boolean> {
  return (await api.put<void>("/api/admin/settings", s)).kind === "ok";
}
export async function fetchUsers(q: string): Promise<AdminUser[]> {
  const r = await api.get<{ users: AdminUser[] }>(`/api/admin/users?q=${encodeURIComponent(q)}`);
  return r.kind === "ok" ? r.value.users : [];
}
export async function fetchUsage(days = 14): Promise<Usage | null> {
  const r = await api.get<Usage>(`/api/admin/usage?days=${days}`);
  return r.kind === "ok" ? r.value : null;
}
export async function setUnlimited(id: string, on: boolean): Promise<boolean> {
  return (await api.post<void>(`/api/admin/users/${id}/unlimited`, { on })).kind === "ok";
}
export type HelpRequest = { id: number; email: string | null; message: string; context: Record<string, string>; at: string };
export async function fetchHelp(): Promise<HelpRequest[]> {
  const r = await api.get<{ requests: HelpRequest[] }>("/api/admin/help");
  return r.kind === "ok" ? r.value.requests : [];
}
export async function helpDone(id: number): Promise<boolean> {
  return (await api.post<void>(`/api/admin/help/${id}/done`)).kind === "ok";
}
export async function grant(id: string, plan: string, days: number, note: string): Promise<boolean> {
  return (await api.post<void>(`/api/admin/users/${id}/grant`, { plan, days, note })).kind === "ok";
}

/** P2c: the plan waitlist. Null when it didn't load (shown as an error, not as "nobody"). */
export type Waitlist = { counts: { plan: string; bd: number; intl: number }[];
  entries: { email: string | null; plan: string; region: "bd" | "intl"; timeZone: string | null; at: string }[] };
export async function fetchWaitlist(): Promise<Waitlist | null> {
  const r = await api.get<Waitlist>("/api/admin/waitlist");
  return r.kind === "ok" ? r.value : null;
}
