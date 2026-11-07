export type Focus = { word: string; kind: "SOUND" | "MISSING" | "EXTRA_VOWEL"; title: string; tip: string };
export type Outcome = { level: "NAILED" | "CLOSER" | "ONCE_MORE"; heard: boolean; focus: Focus[] };
export type Pattern = { key: string; title: string; example: string; tip: string; sentences: string[] };

export type AttemptReply = { kind: "ok"; outcome: Outcome } | { kind: "busy" } | { kind: "slow-down" } | { kind: "failed" };

/** POST /api/attempts: the WAV and the sentence the learner read (US-4.3). */
export async function postAttempt(wav: Blob, reference: string): Promise<AttemptReply> {
  const form = new FormData();
  form.append("reference", reference);
  form.append("audio", wav, "attempt.wav");
  try {
    const res = await fetch("/api/attempts", { method: "POST", credentials: "same-origin", headers: { "X-Bolo": "1" }, body: form });
    if (res.ok) return { kind: "ok", outcome: (await res.json()) as Outcome };
    if (res.status === 429) return { kind: "slow-down" };
    if (res.status === 503) return { kind: "busy" };
    return { kind: "failed" };
  } catch {
    return { kind: "failed" };
  }
}

export async function fetchPractice(): Promise<Pattern[]> {
  try {
    const res = await fetch("/api/practice", { credentials: "same-origin" });
    return res.ok ? ((await res.json()) as { patterns: Pattern[] }).patterns : [];
  } catch {
    return [];
  }
}

export const RESULT: Record<Outcome["level"], { text: string; className: string; icon: string }> = {
  NAILED: { text: "Nailed it", className: "bo-result bo-result--nailed", icon: "check_circle" },
  CLOSER: { text: "Closer", className: "bo-result bo-result--closer", icon: "north_east" },
  ONCE_MORE: { text: "Let's try once more", className: "bo-result bo-result--once", icon: "replay" },
};
