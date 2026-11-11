/** The free plan as the server counts it (ACC-5, US-8.7): null for guests and unlimited learners. */
export type FreeTalks = { window: "first_week" | "month"; left: number; total: number; resetsOn: string | null;
  /** P2a: free, basic, premium or ultimate. */
  plan?: string };

const PLAN_NAME: Record<string, string> = { basic: "Basic", premium: "Premium", ultimate: "Ultimate" };
/** The paid plan's name ("Premium"), or null on the free plan. */
export const planName = (a: FreeTalks) => (a.plan && PLAN_NAME[a.plan]) || null;

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
const WEEK_OVER_SEEN = "bolo.freeWeekOverSeen";

/** "1 December": resetsOn is already a Bangladesh date (YYYY-MM-DD), so no time zone is involved. */
export function comeBackDay(a: FreeTalks): string {
  if (!a.resetsOn) return "";
  const [, m, d] = a.resetsOn.split("-").map(Number);
  return `${d} ${MONTHS[m - 1]}`;
}

/** ScreenHomeFree's footnote. */
export function counterLine(a: FreeTalks): string {
  const paid = planName(a);
  if (paid) return a.left === 0 && a.resetsOn ? `Talks come back on ${comeBackDay(a)}` : `${a.left} of ${a.total} ${paid} talks left this month`;
  if (a.left === 0 && a.resetsOn) return `Free talks come back on ${comeBackDay(a)}`;
  return a.window === "first_week"
    ? `${a.left} of ${a.total} free talks left in your first week`
    : `${a.left} of ${a.total} free talks left this month`;
}

/** ScreenFreeLimit's title. */
export function limitTitle(a: FreeTalks): string {
  const paid = planName(a);
  if (paid) return `You've had this month's ${a.total} ${paid} talks`;
  return a.window === "first_week" ? `You've had your ${a.total} free talks for this week` : `You've had this month's ${a.total} free talks`;
}

/** Once, the first time Home sees the monthly window: the free week is over. Remembered in this browser. */
export function freeWeekOverNote(a: FreeTalks): string | null {
  // Only the free plan has a free week; a paid month is not "the free week is over".
  if (a.window !== "month" || planName(a)) return null;
  try {
    if (window.localStorage.getItem(WEEK_OVER_SEEN)) return null;
    window.localStorage.setItem(WEEK_OVER_SEEN, "1");
  } catch {
    return null;
  }
  return `Your free week is over. From now on you get ${a.total} free talks a month.`;
}

export const FREE_WEEK_OVER_KEY = WEEK_OVER_SEEN;
