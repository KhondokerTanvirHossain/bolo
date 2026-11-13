import { api } from "@/api/browser";

/** DBF-8: whether to ask "How was today?" after this talk's feedback (the server counts the talks). */
export async function fetchAsk(talkId: string): Promise<boolean> {
  const r = await api.get<{ ask: boolean }>(`/api/talk/${talkId}/rating`);
  return r.kind === "ok" && r.value.ask === true;
}

/** A face (1–5), or null for "Not now"; the note only with a score. */
export async function saveRating(talkId: string, score: number | null, note?: string): Promise<boolean> {
  const body = note ? { score, note } : { score };
  return (await api.put<void>(`/api/talk/${talkId}/rating`, body)).kind === "ok";
}
