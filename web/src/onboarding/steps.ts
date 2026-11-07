/** Onboarding as a pure state machine, following the locked desktop prototype: start → welcome → consent → mic → setup. */
export type Step = "start" | "welcome" | "consent" | "mic" | "setup";

export type OnboardingState = {
  step: Step;
  over13: boolean;
  /** null until the learner chooses; nothing is pre-selected (US-1.1a). */
  keepRecordings: boolean | null;
  micDeviceId: string | null;
  /** Barge-in is allowed only when this is true (TLK-15). */
  headphones: boolean;
};

export type OnboardingAction =
  | { type: "go"; step: Step }
  | { type: "toggleOver13" }
  | { type: "chooseConsent"; keep: boolean }
  | { type: "chooseMic"; deviceId: string }
  | { type: "setHeadphones"; headphones: boolean };

export const initialState: OnboardingState = {
  step: "start",
  over13: false,
  keepRecordings: null,
  micDeviceId: null,
  headphones: false,
};

export const STORAGE_KEY = "bolo.onboarding.v1";

const ORDER: Step[] = ["start", "welcome", "consent", "mic", "setup"];

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
  }
}

/** The server is the record of consent: without it, nothing past the consent step may run. */
export function reconcile(state: OnboardingState, serverHasConsent: boolean): OnboardingState {
  const pastConsent = ORDER.indexOf(state.step) > ORDER.indexOf("consent");
  if (serverHasConsent || !pastConsent) return state;
  return { ...state, step: "consent", keepRecordings: null };
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
