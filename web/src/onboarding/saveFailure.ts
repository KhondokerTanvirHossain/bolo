import type { ApiResult } from "@/api/browser";

/** Why a save failed, in terms the learner can act on. */
export type SaveFailure = null | "network" | "busy";

export function toSaveFailure(res: ApiResult<unknown>): SaveFailure {
  if (res.kind === "ok") return null;
  return res.kind === "refused" && res.code === "slow-down" ? "busy" : "network";
}

export function failureCopy(failure: SaveFailure): string {
  return failure === "busy"
    ? "Lots of people are joining right now. Try again in a minute."
    : "That didn't save. Check your connection, then try again.";
}
