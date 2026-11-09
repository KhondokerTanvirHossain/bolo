import { api } from "@/api/browser";
import type { Outcome } from "@/practice/attempts";

/** GET /api/talk/{id}/drills (DRL-1): up to 3, made from that talk's fixes. */
export type PairsDrill = { key: string; kind: "pairs"; title: string; pattern: string; tip: string;
  listen: { a: string; b: string; target: string }[]; say: string[] };
export type SayBetterDrill = { key: string; kind: "sayBetter"; title: string; said: string; better: string; keyWords: string[] };
export type ShadowDrill = { key: string; kind: "shadow"; title: string; sentence: string };
export type Drill = PairsDrill | SayBetterDrill | ShadowDrill;
export type Level = Outcome["level"];

export async function fetchDrills(talkId: string): Promise<Drill[] | null> {
  const r = await api.get<{ drills: Drill[] }>(`/api/talk/${talkId}/drills`);
  return r.kind === "ok" ? r.value.drills : null;
}

/** The Coach saying a word or sentence of this talk's drills. */
export const sayUrl = (talkId: string, text: string) => `/api/talk/${talkId}/drills/say?text=${encodeURIComponent(text)}`;

/** Tries per item before "Good effort. We'll come back to this one." (DRL-6). */
export const TRIES = 3;
export const MAX_DRILLS = 3;

// ---- the skip queue (DRL-5): this browser only, by drill key; 3 skips retire a drill -------------------------------

const QUEUE = "bolo.drillQueue";
export type Queued = { drill: Drill; talkId: string; skips: number };
export type Planned = { drill: Drill; talkId: string };

export function loadQueue(): Queued[] {
  try {
    return JSON.parse(window.localStorage.getItem(QUEUE) ?? "[]") as Queued[];
  } catch {
    return [];
  }
}

function saveQueue(q: Queued[]) {
  try {
    window.localStorage.setItem(QUEUE, JSON.stringify(q));
  } catch {
    // storage unavailable: skipped drills just don't come back
  }
}

export function skipDrill(drill: Drill, talkId: string) {
  const q = loadQueue();
  const found = q.find((x) => x.drill.key === drill.key);
  if (found) found.skips += 1; else q.push({ drill, talkId, skips: 1 });
  saveQueue(q.filter((x) => x.skips < 3));
}

export function finishDrill(drill: Drill) {
  saveQueue(loadQueue().filter((x) => x.drill.key !== drill.key));
}

/** Closing midway (US-5.5): the drills not done yet come back next time (without counting as a skip). */
export function queueRest(rest: Planned[]) {
  const q = loadQueue();
  for (const r of rest) if (!q.some((x) => x.drill.key === r.drill.key)) q.push({ ...r, skips: 0 });
  saveQueue(q);
}

/** This run: queued drills first, then this talk's, never the same twice, at most 3 (R5). */
export function withQueue(drills: Drill[], talkId: string): Planned[] {
  const out: Planned[] = [];
  for (const q of loadQueue()) if (out.length < MAX_DRILLS) out.push({ drill: q.drill, talkId: q.talkId });
  for (const d of drills) if (out.length < MAX_DRILLS && !out.some((p) => p.drill.key === d.key)) out.push({ drill: d, talkId });
  return out;
}

// ---- the end (DRL-7) -----------------------------------------------------------------------------------------------

export type DrillResult = { title: string; first?: Level; best?: Level; skipped?: boolean };
const WORD: Record<Level, string> = { NAILED: "Nailed it", CLOSER: "Closer", ONCE_MORE: "Once more" };
const RANK: Record<Level, number> = { ONCE_MORE: 0, CLOSER: 1, NAILED: 2 };
export const better = (a: Level | undefined, b: Level) => (!a || RANK[b] > RANK[a] ? b : a);

/** One line per drill: what moved, never what failed. */
export function summaryLines(results: DrillResult[]): string[] {
  return results.flatMap((r) => {
    if (r.skipped) return [`${r.title}: Skipped. It comes back next time.`];
    if (!r.first || !r.best) return [];
    return [r.first === r.best ? `${r.title}: ${WORD[r.best]}` : `${r.title}: ${WORD[r.first]} → ${WORD[r.best]}`];
  });
}

/** Say it better (US-5.4): round 1 the whole sentence, round 2 the key words hidden, round 3 nothing (from memory). */
export function hideKeyWords(sentence: string, keyWords: string[], round: number): string | null {
  if (round >= 3) return null;
  if (round <= 1) return sentence;
  const keys = new Set(keyWords.map((w) => w.toLowerCase()));
  return sentence.replace(/[A-Za-z']+/g, (w) => (keys.has(w.toLowerCase()) ? "____" : w));
}
