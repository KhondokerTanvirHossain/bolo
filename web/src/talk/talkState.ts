import type { MicProblem } from "@/audio/micErrors";

/** What the talk screen shows. Java decides every phase; the browser only renders what the relay says (TLK-16). */
export type TalkPhase =
  | "idle" | "connecting" | "buddy_speaking" | "your_turn" | "thinking" | "ended" | "busy" | "no_voice" | "no_mic";

export type Summary = { turns: number; p50Ms: number | null; p90Ms: number | null };

export type TalkState = {
  phase: TalkPhase;
  talkId: string | null;
  minutes: number;
  caption: string;
  captionTurn: number;
  summary?: Summary;
  micProblem?: MicProblem;
};

export type ServerMessage =
  | { type: "state"; value: "buddy_speaking" | "your_turn" | "thinking" }
  | { type: "caption"; turn: number; text: string }
  | { type: "turn_end"; turn: number }
  | { type: "buddy_done"; turn: number }
  | { type: "ended"; reason: string; turns: number; p50Ms: number | null; p90Ms: number | null };

export type TalkAction =
  | ServerMessage
  | { type: "connecting" }
  | { type: "started"; minutes: number; talkId?: string }
  | { type: "busy" }
  | { type: "no_mic"; problem: MicProblem }
  | { type: "socket_closed" }
  | { type: "reset" };

export const initialTalk: TalkState = { phase: "idle", talkId: null, minutes: 5, caption: "", captionTurn: -1 };

const FINAL: TalkPhase[] = ["ended", "busy", "no_voice", "no_mic", "idle"];

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
      return FINAL.includes(state.phase) && state.phase !== "idle" ? state : { ...state, phase: action.value };
    case "caption":
      return action.turn === state.captionTurn
        ? { ...state, caption: `${state.caption} ${action.text}`.trim() }
        : { ...state, caption: action.text, captionTurn: action.turn };
    case "ended": {
      const summary = { turns: action.turns, p50Ms: action.p50Ms, p90Ms: action.p90Ms };
      return { ...state, phase: action.reason === "voice_unavailable" ? "no_voice" : "ended", summary };
    }
    case "socket_closed":
      return FINAL.includes(state.phase) ? state : { ...state, phase: "no_voice" };
    default:
      return state;
  }
}
