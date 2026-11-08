import { api } from "@/api/browser";

export type Level = "BUILDING" | "GOOD" | "STRONG";

export type Fix = {
  id?: string;
  kind: "sound" | "upgrade" | "habit";
  title: string;
  example?: string;
  said?: string;
  saidHighlight?: string;
  better?: string;
  betterHighlight?: string;
  changed?: string[];
  tip?: string;
  clip?: string | null;
  model?: string | null;
  count?: number;
  word?: string;
  averageWords?: number;
};

export type Debrief = {
  status: "scoring" | "ready" | "too_short" | "failed";
  strength?: { text: string; clip: string | null };
  fixes?: Fix[];
  /** US-8.6: the talk's audio was deleted; clips are null, the feedback stays. */
  recordingDeleted?: boolean;
  skills?: { pronunciation: Level | null; fluency: Level; sentenceBuilding: Level; vocabulary: Level };
  firstSession?: boolean;
  latency?: { p50Ms: number | null; p90Ms: number | null };
};

export const POLL = { intervalMs: 1500, timeoutMs: 45_000 } as const;

type Deps = { fetch: () => Promise<Debrief | null>; now: () => number; sleep: (ms: number) => Promise<void> };

/** Polls until the Coach is done (ASM-7 aims for 15 s). Null means this talk has no debrief; a long wait reads as failed. */
export async function pollDebrief(deps: Deps, onUpdate: (d: Debrief) => void, isCancelled: () => boolean = () => false): Promise<Debrief | null> {
  const start = deps.now();
  while (!isCancelled()) {
    const d = await deps.fetch();
    if (d === null) return null;
    onUpdate(d);
    if (d.status !== "scoring") return d;
    if (deps.now() - start >= POLL.timeoutMs) {
      const failed: Debrief = { status: "failed" };
      onUpdate(failed);
      return failed;
    }
    await deps.sleep(POLL.intervalMs);
  }
  return null;
}

export async function fetchDebrief(talkId: string): Promise<Debrief | null> {
  const res = await api.get<Debrief>(`/api/talk/${talkId}/debrief`);
  if (res.kind === "ok") return res.value;
  if (res.kind === "offline") return { status: "scoring" };
  return null;
}

export async function disputeFix(talkId: string, fixId: string): Promise<boolean> {
  return (await api.post<void>(`/api/talk/${talkId}/debrief/fixes/${fixId}/dispute`)).kind === "ok";
}
