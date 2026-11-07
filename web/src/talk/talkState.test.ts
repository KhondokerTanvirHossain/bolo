import { describe, expect, it } from "vitest";
import { initialTalk, talk } from "./talkState";

describe("talk state", () => {
  it("follows the server's state", () => {
    let s = talk(initialTalk, { type: "started", minutes: 10 });
    expect(s.minutes).toBe(10);
    s = talk(s, { type: "state", value: "your_turn" });
    expect(s.phase).toBe("your_turn");
  });

  it("appends captions within a turn and replaces them on a new turn", () => {
    let s = talk(initialTalk, { type: "caption", turn: 0, text: "Hi, I'm Bolo." });
    s = talk(s, { type: "caption", turn: 0, text: "What did you do today?" });
    expect(s.caption).toBe("Hi, I'm Bolo. What did you do today?");
    s = talk(s, { type: "caption", turn: 2, text: "Nice." });
    expect(s.caption).toBe("Nice.");
  });

  it("keeps the summary when the talk ends", () => {
    const s = talk(initialTalk, { type: "ended", reason: "learner_ended", turns: 3, p50Ms: 900, p90Ms: 1400 });
    expect(s.phase).toBe("ended");
    expect(s.summary).toEqual({ turns: 3, p50Ms: 900, p90Ms: 1400 });
  });

  it("a lost voice is not a normal end", () => {
    const s = talk(initialTalk, { type: "ended", reason: "voice_unavailable", turns: 1, p50Ms: null, p90Ms: null });
    expect(s.phase).toBe("no_voice");
  });

  it("a socket that closes before the end means Bolo lost its voice", () => {
    let s = talk(initialTalk, { type: "state", value: "your_turn" });
    s = talk(s, { type: "socket_closed" });
    expect(s.phase).toBe("no_voice");
    const ended = talk(talk(initialTalk, { type: "ended", reason: "time_up", turns: 2, p50Ms: 1, p90Ms: 1 }), { type: "socket_closed" });
    expect(ended.phase).toBe("ended");
  });

  it("ignores server state after the end", () => {
    const s = talk(talk(initialTalk, { type: "ended", reason: "time_up", turns: 2, p50Ms: 1, p90Ms: 1 }), { type: "state", value: "your_turn" });
    expect(s.phase).toBe("ended");
  });
});
