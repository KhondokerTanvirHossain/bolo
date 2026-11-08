import { QUESTIONS } from "@/onboarding/questions";

/** The goal's words as the learner chose them in setup ("Job interviews"), keyed by the stable value. */
export const GOAL_LABEL: Record<string, string> = Object.fromEntries(
  QUESTIONS.flatMap((q) => q.chips.map((c) => [c.value, c.label] as const)),
);
