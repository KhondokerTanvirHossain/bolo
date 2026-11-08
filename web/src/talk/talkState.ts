import type { MicProblem } from "@/audio/micErrors";

/** What the talk screen shows. Java decides every phase; the browser only renders what the relay says (TLK-16). */
export type TalkPhase =
  | "idle" | "connecting" | "buddy_speaking" | "your_turn" | "thinking" | "paused" | "crisis" | "ended" | "busy"
  | "no_voice" | "no_mic";

export type Crisis = { title: string; lines: string[]; help: { name: string; number: string; hours: string }[] };

export type Summary = { turns: number; p50Ms: number | null; p90Ms: number | null };

export type TalkState = {
  phase: TalkPhase;
  talkId: string | null;
  minutes: number;
  caption: string;
  captionTurn: number;
  summary?: Summary;
  micProblem?: MicProblem;
  /** TLK-6: two sentence starters, shown until the learner's turn ends. */
  hint: string[] | null;
  /** TLK-14: the approved crisis content, which stays on screen. */
  crisis: Crisis | null;
  /** US-2.7: "Slower please" was heard; ask whether to keep the slower Bolo. */
  askKeepSlower: boolean;
};

export type ServerMessage =
  | { type: "state"; value: "buddy_speaking" | "your_turn" | "thinking" }
  | { type: "caption"; turn: number; text: string }
  | { type: "turn_end"; turn: number }
  | { type: "buddy_done"; turn: number }
  | { type: "ended"; reason: string; turns: number; p50Ms: number | null; p90Ms: number | null }
  | { type: "hint"; starters: string[] }
  | { type: "paused"; reason: string }
  | { type: "resumed" }
  | ({ type: "crisis" } & Crisis)
  | { type: "slower" };

export type TalkAction =
  | ServerMessage
  | { type: "connecting" }
  | { type: "started"; minutes: number; talkId?: string }
  | { type: "busy" }
  | { type: "no_mic"; problem: MicProblem }
  | { type: "socket_closed" }
  | { type: "reset" }
  | { type: "keep_slower_answered" };

export const initialTalk: TalkState = { phase: "idle", talkId: null, minutes: 5, caption: "", captionTurn: -1, hint: null, crisis: null, askKeepSlower: false };

const FINAL: TalkPhase[] = ["ended", "busy", "no_voice", "no_mic", "idle", "crisis"];

export function talk(state: TalkState, action: TalkAction): TalkState {
  switch (action.type) {
    case "connecting":
      return { ...initialTalk, phase: "connecting" };
    case "started":
      return { ...state, minutes: action.minutes, talkId: action.talkId ?? state.talkId };
    case "busy":
      return { ...state, phase: "busy" };
    case "no_mic":
      return { ...state, phase: "no_mic", micProblem: action.problem };
    case "reset":
      return initialTalk;
    case "state":
      if (FINAL.includes(state.phase) && state.phase !== "idle") return state;
      // While paused, only "resumed" brings the talk back (a late Bolo line must not reopen the mic).
      if (state.phase === "paused") return state;
      return { ...state, phase: action.value, hint: action.value === "your_turn" ? state.hint : null };
    case "slower":
      return { ...state, askKeepSlower: true };
    case "keep_slower_answered":
      return { ...state, askKeepSlower: false };
    case "hint":
      return { ...state, hint: action.starters };
    case "paused":
      return FINAL.includes(state.phase) ? state : { ...state, phase: "paused", hint: null };
    case "resumed":
      return state.phase === "paused" ? { ...state, phase: "thinking" } : state;
    case "crisis":
      return { ...state, phase: "crisis", hint: null, crisis: { title: action.title, lines: action.lines, help: action.help } };
    case "caption":
      return action.turn === state.captionTurn
        ? { ...state, caption: `${state.caption} ${action.text}`.trim() }
        : { ...state, caption: action.text, captionTurn: action.turn };
    case "ended": {
      if (state.phase === "crisis") return state;
      const summary = { turns: action.turns, p50Ms: action.p50Ms, p90Ms: action.p90Ms };
      return { ...state, phase: action.reason === "voice_unavailable" ? "no_voice" : "ended", summary };
    }
    case "socket_closed":
      return FINAL.includes(state.phase) ? state : { ...state, phase: "no_voice" };
    default:
      return state;
  }
}
