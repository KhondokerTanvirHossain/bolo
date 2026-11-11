import { api } from "@/api/browser";

/** P2d: invite 3 friends, get a month of Premium. The link is ?ref=CODE; it rides along with Google sign-in. */
const KEY = "bolo.ref";
const CODE = /^[a-z0-9]{8}$/;

export type Referral = {
  offer: boolean;
  code: string;
  friends: string[];
  counted: number;
  rewards: number;
  maxRewards: number;
  premiumUntil: string | null;
  newReward: boolean;
};

export function takeRefFromUrl(): void {
  try {
    const url = new URL(window.location.href);
    const code = url.searchParams.get("ref");
    if (code === null) return;
    if (CODE.test(code)) window.sessionStorage.setItem(KEY, code);
    url.searchParams.delete("ref");
    window.history.replaceState(null, "", url.pathname + url.search + url.hash);
  } catch {
    // no ref then
  }
}

export function currentRef(): string | null {
  try {
    return window.sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export const inviteLink = (code: string, origin: string) => `${origin}/?ref=${code}`;

/** Neutral words, never the learner's level; they choose who gets it (US-8.10). */
const message = (link: string) => `I'm practising speaking English with Bolo. Try it: ${link}`;

/** The device's share sheet, else copy the link. Bolo never sends anything itself. */
export async function shareLink(link: string): Promise<"shared" | "copied" | "cancelled" | "failed"> {
  if (typeof navigator.share === "function") {
    try {
      await navigator.share({ text: message(link) });
      return "shared";
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return "cancelled";
    }
  }
  return (await copyText(link)) ? "copied" : "failed";
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export async function fetchReferral(): Promise<Referral | null> {
  const r = await api.get<Referral>("/api/referral");
  return r.kind === "ok" ? r.value : null;
}

export async function markRewardSeen(): Promise<void> {
  await api.post<void>("/api/referral/seen");
}

/** "A month of Premium is yours. Until 12 December." */
export function rewardNote(r: Referral): string | null {
  if (!r.newReward || !r.premiumUntil) return null;
  const day = new Date(r.premiumUntil).toLocaleDateString("en-GB", { day: "numeric", month: "long", timeZone: "Asia/Dhaka" });
  return `A month of Premium is yours. Until ${day}.`;
}
