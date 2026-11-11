import { api } from "@/api/browser";

/** P2c: the pricing page. ৳ in Bangladesh (bKash), $ elsewhere (cards later, so a waitlist). */
export type Region = "bd" | "intl";
export type PaidPlan = { plan: string; monthlyTalks: number; priceBdt: number; priceUsd: number };
export type MyWaitlist = { plan: string; region: Region } | null;

const KEY = "bolo.region";

/** The saved switch first; else Bangladesh when the browser's time zone is Dhaka. Never throws. */
export function regionGuess(): Region {
  try {
    const saved = window.localStorage.getItem(KEY);
    if (saved === "bd" || saved === "intl") return saved;
  } catch {
    // storage blocked
  }
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone === "Asia/Dhaka" ? "bd" : "intl";
  } catch {
    return "intl";
  }
}

export function saveRegion(r: Region): void {
  try {
    window.localStorage.setItem(KEY, r);
  } catch {
    // storage blocked: the switch still works for this visit
  }
}

export function timeZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone?.slice(0, 64) ?? null;
  } catch {
    return null;
  }
}

const money = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(2));

export function priceLabel(p: PaidPlan, region: Region): string {
  return region === "bd" ? `৳${money(p.priceBdt)} a month` : `$${money(p.priceUsd)} a month`;
}

export const planTitle = (plan: string) => plan.charAt(0).toUpperCase() + plan.slice(1);

export async function fetchPlans(): Promise<PaidPlan[] | null> {
  const r = await api.get<{ plans: PaidPlan[] }>("/api/plans");
  return r.kind === "ok" ? r.value.plans : null;
}

export async function myWaitlist(): Promise<MyWaitlist> {
  const r = await api.get<{ plan: string; region: Region } | undefined>("/api/waitlist/me");
  return r.kind === "ok" && r.value ? r.value : null;
}

export async function joinWaitlist(plan: string, region: Region): Promise<"ok" | "sign-in" | "failed"> {
  const r = await api.post<void>("/api/waitlist", { plan, region, timeZone: timeZone() });
  if (r.kind === "ok") return "ok";
  return r.kind === "signed-out" || (r.kind === "refused" && r.code === "sign-in") ? "sign-in" : "failed";
}
