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

// ---- the skip queue (DRL-5): this browser only, each learner's own, by drill key; 3 skips (or closes) retire a drill --

const QUEUE = "bolo.drillQueue";
export type Queued = { drill: Drill; talkId: string; skips: number; learnerId: string };
export type Planned = { drill: Drill; talkId: string };

function readAll(): Queued[] {
  try {
    return JSON.parse(window.localStorage.getItem(QUEUE) ?? "[]") as Queued[];
  } catch {
    return [];
  }
}

function writeAll(q: Queued[]) {
  try {
    window.localStorage.setItem(QUEUE, JSON.stringify(q));
  } catch {
    // storage unavailable: skipped drills just don't come back
  }
}

/** A learner's queue only: a shared browser never shows one person's drills (their own words) to another. */
export function loadQueue(learnerId: string): Queued[] {
  return readAll().filter((x) => x.learnerId === learnerId);
}

function bump(drill: Drill, talkId: string, learnerId: string) {
  const all = readAll();
  const found = all.find((x) => x.learnerId === learnerId && x.drill.key === drill.key);
  if (found) found.skips += 1; else all.push({ drill, talkId, skips: 1, learnerId });
  writeAll(all.filter((x) => x.skips < 3));
}

/** @return true when this skip retired the drill (its third). */
export function skipDrill(drill: Drill, talkId: string, learnerId: string): boolean {
  const before = loadQueue(learnerId).find((x) => x.drill.key === drill.key)?.skips ?? 0;
  bump(drill, talkId, learnerId);
  return before + 1 >= 3;
}

export function finishDrill(drill: Drill, learnerId: string) {
  writeAll(readAll().filter((x) => !(x.learnerId === learnerId && x.drill.key === drill.key)));
}

/** Closing midway (US-5.5): the drills not done come back next time; a close counts like a skip, so none loops for ever. */
export function queueRest(rest: Planned[], learnerId: string) {
  for (const r of rest) bump(r.drill, r.talkId, learnerId);
}

/**
 * This run: queued drills first (at most 2), then the chosen talk's, never the same twice, at most 3 (R5). The talk the
 * learner chose always gets a place when it has drills.
 */
export function withQueue(drills: Drill[], talkId: string, learnerId: string): Planned[] {
  const out: Planned[] = [];
  const queued = loadQueue(learnerId).filter((q) => !drills.some((d) => d.key === q.drill.key));
  const room = drills.length > 0 ? MAX_DRILLS - 1 : MAX_DRILLS;
  for (const q of queued) if (out.length < room) out.push({ drill: q.drill, talkId: q.talkId });
  for (const d of drills) if (out.length < MAX_DRILLS) out.push({ drill: d, talkId });
  return out;
}

// ---- the end (DRL-7) -----------------------------------------------------------------------------------------------

export type DrillResult = { title: string; first?: Level; best?: Level; skipped?: boolean; retired?: boolean };
const WORD: Record<Level, string> = { NAILED: "Nailed it", CLOSER: "Closer", ONCE_MORE: "Once more" };
const RANK: Record<Level, number> = { ONCE_MORE: 0, CLOSER: 1, NAILED: 2 };
export const better = (a: Level | undefined, b: Level) => (!a || RANK[b] > RANK[a] ? b : a);

/** One line per drill: what moved, never what failed. */
export function summaryLines(results: DrillResult[]): string[] {
  return results.flatMap((r) => {
    if (r.skipped) return [r.retired ? `${r.title}: Skipped.` : `${r.title}: Skipped. It comes back next time.`];
    if (!r.first || !r.best) return [];
    // Only movement and effort, never a failure (ScreenDrillDone).
    if (r.best === "ONCE_MORE") return [`${r.title}: Good effort. It comes back next time.`];
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
