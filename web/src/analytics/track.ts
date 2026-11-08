import { api } from "@/api/browser";

/**
 * PRD §10 funnel events: no audio, no transcript text, only numbers, booleans and short keys. Nothing exists on the
 * server before consent (no learner yet), so events that happen earlier wait here and are sent right after consent.
 */
export type EventProps = Record<string, number | boolean | string>;

const QUEUE_KEY = "bolo.events.queue";
const FIRST_OPEN_KEY = "bolo.firstOpen";
const FIRST_WORD_KEY = "bolo.firstWordSent";
const MAX_QUEUED = 20;

type Queued = { name: string; props: EventProps };

function store(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function queue(): Queued[] {
  try {
    return JSON.parse(store()?.getItem(QUEUE_KEY) ?? "[]") as Queued[];
  } catch {
    return [];
  }
}

function setQueue(q: Queued[]) {
  try {
    store()?.setItem(QUEUE_KEY, JSON.stringify(q.slice(-MAX_QUEUED)));
  } catch {
    // a per-browser convenience only
  }
}

/** Sends one event; before consent (signed out) it waits in the queue. Never throws. */
export async function track(name: string, props: EventProps = {}): Promise<void> {
  try {
    const res = await api.post<void>("/api/events", { name, props });
    if (res.kind === "signed-out") setQueue([...queue(), { name, props }]);
  } catch {
    // analytics must never break the app
  }
}

/** Sends what waited for a session, in order; anything still refused as signed out stays queued. */
export async function flushEvents(): Promise<void> {
  const waiting = queue();
  if (waiting.length === 0) return;
  setQueue([]);
  for (let i = 0; i < waiting.length; i++) {
    try {
      const res = await api.post<void>("/api/events", waiting[i]);
      if (res.kind === "signed-out") {
        setQueue([...waiting.slice(i), ...queue()]);
        return;
      }
    } catch {
      setQueue([...waiting.slice(i), ...queue()]);
      return;
    }
  }
}

/**
 * First launch on this browser: remembers when, and queues app_first_open (sent once a session exists). A browser that
 * used Bolo before tracking began ({@code returning}) isn't a new opener and has no honest first word: it's marked done.
 */
export function markFirstOpen(now: number = Date.now(), returning = false): void {
  const s = store();
  if (!s || s.getItem(FIRST_OPEN_KEY)) return;
  if (returning) {
    try {
      s.setItem(FIRST_OPEN_KEY, "returning");
      s.setItem(FIRST_WORD_KEY, "1");
    } catch {
      // nothing to record
    }
    return;
  }
  try {
    s.setItem(FIRST_OPEN_KEY, String(now));
  } catch {
    return;
  }
  setQueue([...queue(), { name: "app_first_open", props: {} }]);
}

/** R4 / Gate 1: the first spoken answer (the Day 1 clip, or the first talk turn). Once per browser. */
export async function firstWordSpoken(now: number = Date.now()): Promise<void> {
  const s = store();
  if (!s || s.getItem(FIRST_WORD_KEY)) return;
  const first = Number(s.getItem(FIRST_OPEN_KEY));
  try {
    s.setItem(FIRST_WORD_KEY, "1");
  } catch {
    return;
  }
  // A browser that opened Bolo before this was tracked has no start time: nothing honest to report.
  if (!first) return;
  await track("first_word_spoken", { seconds_since_first_open: Math.max(0, Math.round((now - first) / 1000)) });
}
