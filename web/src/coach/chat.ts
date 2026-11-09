import { api } from "@/api/browser";

export type Mark = { turn: number; start: number; end: number; fix: number; title: string };
export type Chat = { topic: string; turns: { speaker: "bolo" | "learner"; text: string }[]; marks: Mark[] };
export type Segment = { text: string; mark?: Mark };

/** GET /api/talk/{id}/chat (DBF-6). null when it isn't there (not yours, or no feedback yet). */
export async function fetchChat(talkId: string): Promise<Chat | null> {
  const res = await api.get<Chat>(`/api/talk/${talkId}/chat`);
  return res.kind === "ok" ? res.value : null;
}

/**
 * Splits a turn's text into plain and marked runs. Every mark edge is a cut; each piece takes the innermost mark that
 * covers it, so a sound word inside an upgrade phrase stays its own mark and the text is never repeated or broken.
 */
export function segments(text: string, marks: Mark[]): Segment[] {
  const ok = marks.filter((m) => m.start >= 0 && m.end <= text.length && m.end > m.start);
  const cuts = [...new Set([0, text.length, ...ok.flatMap((m) => [m.start, m.end])])].sort((a, b) => a - b);
  const out: Segment[] = [];
  for (let i = 0; i < cuts.length - 1; i++) {
    const [a, b] = [cuts[i], cuts[i + 1]];
    const covering = ok.filter((m) => m.start <= a && m.end >= b).sort((x, y) => (x.end - x.start) - (y.end - y.start))[0];
    const last = out[out.length - 1];
    if (last && last.mark === covering) last.text += text.slice(a, b);
    else out.push(covering ? { text: text.slice(a, b), mark: covering } : { text: text.slice(a, b) });
  }
  return out;
}
