import { api } from "@/api/browser";

export type HelpContext = { view: string; talkId?: string };

/** POST /api/help (P2b): the learner's note plus the screen, the last talk and the browser. */
export async function reportProblem(message: string, ctx: HelpContext): Promise<"ok" | "too-many" | "failed"> {
  const r = await api.post<void>("/api/help", {
    message, context: { view: ctx.view, ...(ctx.talkId ? { talkId: ctx.talkId } : {}), browser: navigator.userAgent.slice(0, 200) },
  });
  if (r.kind === "ok") return "ok";
  return r.kind === "refused" && r.code === "slow-down" ? "too-many" : "failed";
}
