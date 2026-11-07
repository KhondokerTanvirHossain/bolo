import { QUESTIONS, type Field, type Question } from "./questions";

/** Onboarding as a pure state machine, following the locked desktop prototype:
 *  start → welcome → consent → mic → setup (3 questions) → day1 → talk. */
export type Step = "start" | "welcome" | "consent" | "mic" | "setup" | "day1" | "talk";

export type OnboardingState = {
  step: Step;
  over13: boolean;
  /** null until the learner chooses; nothing is pre-selected (US-1.1a). */
  keepRecordings: boolean | null;
  micDeviceId: string | null;
  /** Barge-in is allowed only when this is true (TLK-15). */
  headphones: boolean;
  /** The mic check's verdict; "noisy"/"quiet"/"skipped" later hides low-confidence feedback (US-1.5). */
  micCheck: "great" | "noisy" | "quiet" | "skipped" | null;
  /** Which setup question is showing (0–2) and the stable keys chosen so far. */
  questionIndex: number;
  answers: Partial<Record<Field, string>>;
};

export type OnboardingAction =
  | { type: "go"; step: Step }
  | { type: "toggleOver13" }
  | { type: "chooseConsent"; keep: boolean }
  | { type: "chooseMic"; deviceId: string }
  | { type: "setHeadphones"; headphones: boolean }
  | { type: "forgetMic" }
  | { type: "micChecked"; result: "great" | "noisy" | "quiet" | "skipped" }
  | { type: "answer"; value: string }
  | { type: "nextQuestion" }
  | { type: "prevQuestion" }
  | { type: "profileSaved" };

export const initialState: OnboardingState = {
  step: "start",
  over13: false,
  keepRecordings: null,
  micDeviceId: null,
  headphones: false,
  micCheck: null,
  questionIndex: 0,
  answers: {},
};

export const STORAGE_KEY = "bolo.onboarding.v1";

const ORDER: Step[] = ["start", "welcome", "consent", "mic", "setup", "day1", "talk"];

export function currentQuestion(state: OnboardingState): Question {
  return QUESTIONS[Math.min(state.questionIndex, QUESTIONS.length - 1)]!;
}

export function isAnswered(state: OnboardingState): boolean {
  return state.answers[currentQuestion(state).field] !== undefined;
}

export function isLastQuestion(state: OnboardingState): boolean {
  return state.questionIndex >= QUESTIONS.length - 1;
}

export function onboarding(state: OnboardingState, action: OnboardingAction): OnboardingState {
  switch (action.type) {
    case "go":
      return { ...state, step: action.step };
    case "toggleOver13":
      return { ...state, over13: !state.over13 };
    case "chooseConsent":
      if (!state.over13) return state;
      return { ...state, keepRecordings: action.keep, step: "mic" };
    case "chooseMic":
      return { ...state, micDeviceId: action.deviceId };
    case "setHeadphones":
      return { ...state, headphones: action.headphones };
    case "forgetMic":
      return { ...state, micDeviceId: null };
    case "micChecked":
      return { ...state, micCheck: action.result };
    case "answer":
      return { ...state, answers: { ...state.answers, [currentQuestion(state).field]: action.value } };
    case "nextQuestion":
      // The last question moves on only once the server has the answers (profileSaved).
      if (!isAnswered(state) || isLastQuestion(state)) return state;
      return { ...state, questionIndex: state.questionIndex + 1 };
    case "prevQuestion":
      return { ...state, questionIndex: Math.max(0, state.questionIndex - 1) };
    case "profileSaved":
      return { ...state, step: "day1" };
  }
}

/** The server is the record: without its consent nothing past consent may run, and without the saved answers
 *  nothing past the questions may run. */
export function reconcile(state: OnboardingState, server: { consent: boolean; profile: boolean }): OnboardingState {
  const at = ORDER.indexOf(state.step);
  if (!server.consent && at > ORDER.indexOf("consent")) return { ...state, step: "consent", keepRecordings: null };
  if (!server.profile && at > ORDER.indexOf("setup")) return { ...state, step: "setup" };
  return state;
}

export function load(storage: Storage): OnboardingState {
  try {
    const raw = storage.getItem(STORAGE_KEY);
    if (!raw) return initialState;
    const parsed = JSON.parse(raw) as Partial<OnboardingState>;
    if (!parsed.step || !ORDER.includes(parsed.step)) return initialState;
    return { ...initialState, ...parsed };
  } catch {
    return initialState;
  }
}

export function save(storage: Storage, state: OnboardingState): void {
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Storage blocked (private window, thumbnail capture): onboarding still works, it just won't resume.
  }
}
