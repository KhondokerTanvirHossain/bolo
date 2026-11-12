import type { MicProblem } from "@/audio/micErrors";

/** What the talk screen shows. Java decides every phase; the browser only renders what the relay says (TLK-16). */
export type TalkPhase =
  | "idle" | "connecting" | "buddy_speaking" | "your_turn" | "thinking" | "paused" | "crisis" | "ended" | "busy"
  | "no_voice" | "no_mic"
  /** ACC-2: the 4th guest talk; Home shows the sign-in sheet. */
  | "needs_sign_in"
  /** US-8.7: no free talks left; Home shows when they come back. */
  | "out_of_talks"
  /** P2a: the daily cap is reached for everyone; back tomorrow. */
  | "resting";

export type Crisis = { title: string; lines: string[]; help: { name: string; number: string; hours: string }[] };

export type Summary = { turns: number; p50Ms: number | null; p90Ms: number | null };

/** P3a (TLK-8 changed 2026-11-12): a settled line of the talk, shown as a chat bubble. */
export type ChatTurn = { turn: number; who: "bolo" | "you"; text: string };

/** P3a (TLK-16 changed 2026-11-12): the server's time left, and when it was received; frozen while paused. */
export type Clock = { leftMs: number; at: number; paused: boolean };

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
  /** P3a: the settled turns, oldest first (Bolo's sentences join one bubble per turn). */
  chat: ChatTurn[];
  /** P3a: the learner's words while they speak, exactly as heard; null when nobody is speaking. */
  live: { turn: number; text: string } | null;
  /** P3a: the time left, from the server. */
  clock: Clock | null;
  /** P3a: the Bolo turn R asked to repeat: its bubble replays rather than a second one appearing. */
  replay: number | null;
};

export type ServerMessage =
  | { type: "state"; value: "buddy_speaking" | "your_turn" | "thinking"; leftMs?: number; at?: number }
  | { type: "caption"; turn: number; text: string; repeatOf?: number }
  | { type: "turn_end"; turn: number; text?: string }
  | { type: "you"; turn: number; text: string }
  | { type: "you_drop"; turn: number }
  | { type: "buddy_done"; turn: number }
  | { type: "ended"; reason: string; turns: number; p50Ms: number | null; p90Ms: number | null }
  | { type: "hint"; starters: string[] }
  | { type: "paused"; reason: string; leftMs?: number; at?: number }
  | { type: "resumed"; leftMs?: number; at?: number }
  | ({ type: "crisis" } & Crisis)
  | { type: "slower" };

export type TalkAction =
  | ServerMessage
  | { type: "connecting" }
  | { type: "started"; minutes: number; talkId?: string }
  | { type: "busy" }
  | { type: "needs_sign_in" }
  | { type: "free_limit" }
  | { type: "daily_cap" }
  | { type: "no_mic"; problem: MicProblem }
  | { type: "socket_closed" }
  | { type: "reset" }
  | { type: "keep_slower_answered" };

export const initialTalk: TalkState = { phase: "idle", talkId: null, minutes: 5, caption: "", captionTurn: -1, hint: null, crisis: null,
  askKeepSlower: false, chat: [], live: null, clock: null, replay: null };

const clockOf = (leftMs: number | undefined, at: number | undefined, paused: boolean, prev: Clock | null): Clock | null =>
  leftMs === undefined ? prev : { leftMs, at: at ?? 0, paused };

/** Milliseconds left now (frozen while paused); negative once time is up. */
export function leftNow(clock: Clock | null, now: number): number {
  if (!clock) return 0;
  return clock.paused ? clock.leftMs : clock.leftMs - (now - clock.at);
}

/** "3:42 left", then "1 minute left" for the last minute, then "Wrapping up" (TLK-16 changed 2026-11-12). */
export function timeLeftLabel(clock: Clock | null, now: number): string | null {
  if (!clock) return null;
  const ms = leftNow(clock, now);
  if (ms <= 0) return "Wrapping up";
  if (ms <= 60_000) return "1 minute left";
  const s = Math.ceil(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")} left`;
}

const FINAL: TalkPhase[] = ["ended", "busy", "no_voice", "no_mic", "idle", "crisis", "needs_sign_in", "out_of_talks", "resting"];

export function talk(state: TalkState, action: TalkAction): TalkState {
  switch (action.type) {
    case "connecting":
      return { ...initialTalk, phase: "connecting" };
    case "started":
      return { ...state, minutes: action.minutes, talkId: action.talkId ?? state.talkId };
    case "busy":
      return { ...state, phase: "busy" };
    case "needs_sign_in":
      return { ...state, phase: "needs_sign_in" };
    case "free_limit":
      return { ...state, phase: "out_of_talks" };
    case "daily_cap":
      return { ...state, phase: "resting" };
    case "no_mic":
      return { ...state, phase: "no_mic", micProblem: action.problem };
    case "reset":
      return initialTalk;
    case "state":
      if (FINAL.includes(state.phase) && state.phase !== "idle") return state;
      // While paused, only "resumed" brings the talk back (a late Bolo line must not reopen the mic).
      if (state.phase === "paused") return state;
      return { ...state, phase: action.value, hint: action.value === "your_turn" ? state.hint : null,
        // Review (P3a): a live bubble only lives during the learner's turn.
        live: action.value === "your_turn" ? state.live : null,
        clock: clockOf(action.leftMs, action.at, false, state.clock) };
    case "slower":
      return { ...state, askKeepSlower: true };
    case "keep_slower_answered":
      return { ...state, askKeepSlower: false };
    case "hint":
      return { ...state, hint: action.starters };
    case "paused":
      return FINAL.includes(state.phase) ? state
        : { ...state, phase: "paused", hint: null, live: null, clock: clockOf(action.leftMs, action.at, true, state.clock && { ...state.clock, paused: true }) };
    case "resumed":
      return state.phase === "paused" ? { ...state, phase: "thinking", clock: clockOf(action.leftMs, action.at, false, state.clock) } : state;
    case "crisis":
      return { ...state, phase: "crisis", hint: null, live: null, crisis: { title: action.title, lines: action.lines, help: action.help } };
    case "caption": {
      const classic = action.turn === state.captionTurn
        ? { caption: `${state.caption} ${action.text}`.trim() }
        : { caption: action.text, captionTurn: action.turn };
      // R: the earlier bubble replays its reveal; no second bubble with the same line.
      if (action.repeatOf !== undefined) return { ...state, ...classic, replay: action.repeatOf };
      const last = state.chat.at(-1);
      const chat = last && last.who === "bolo" && last.turn === action.turn
        ? [...state.chat.slice(0, -1), { ...last, text: `${last.text} ${action.text}`.trim() }]
        : [...state.chat, { turn: action.turn, who: "bolo" as const, text: action.text }];
      return { ...state, ...classic, chat, replay: null };
    }
    case "you":
      // A partial that arrives after its turn settled must not reopen it.
      if (state.chat.some((t) => t.turn >= action.turn)) return state;
      return { ...state, live: { turn: action.turn, text: action.text } };
    case "you_drop":
      return state.live?.turn === action.turn ? { ...state, live: null } : state;
    case "turn_end": {
      const text = (action.text ?? state.live?.text ?? "").trim();
      const settled = text && !state.chat.some((t) => t.turn === action.turn);
      return { ...state, live: null, chat: settled ? [...state.chat, { turn: action.turn, who: "you" as const, text }] : state.chat };
    }
    case "ended": {
      if (state.phase === "crisis") return state;
      const summary = { turns: action.turns, p50Ms: action.p50Ms, p90Ms: action.p90Ms };
      return { ...state, phase: action.reason === "voice_unavailable" ? "no_voice" : "ended", summary, live: null, clock: null };
    }
    case "socket_closed":
      return FINAL.includes(state.phase) ? state : { ...state, phase: "no_voice", live: null, clock: null };
    default:
      return state;
  }
}
