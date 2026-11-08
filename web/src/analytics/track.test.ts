// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { firstWordSpoken, flushEvents, markFirstOpen, track } from "./track";


type Sent = { name: string; props: Record<string, unknown> };
let sent: Sent[] = [];
let status = 202;

beforeEach(() => {
  window.localStorage.clear();
  sent = [];
  status = 202;
  vi.stubGlobal("fetch", vi.fn(async (_url: string, init: RequestInit) => {
    if (status === 0) throw new TypeError("offline");
    if (status === 202) sent.push(JSON.parse(String(init.body)) as Sent);
    return new Response(null, { status });
  }));
});
afterEach(() => vi.unstubAllGlobals());

describe("track", () => {
  it("fires first word once per browser", async () => {
    markFirstOpen(Date.now() - 5000);
    await flushEvents();
    sent = [];
    await firstWordSpoken();
    await firstWordSpoken();
    expect(sent.filter((e) => e.name === "first_word_spoken")).toHaveLength(1);
  });

  it("counts the seconds from the first open", async () => {
    markFirstOpen(Date.now() - 37_000);
    await firstWordSpoken();
    expect(sent.find((e) => e.name === "first_word_spoken")?.props.seconds_since_first_open).toBe(37);
  });

  it("never throws when the network fails", async () => {
    status = 0;
    await expect(track("debrief_viewed", { fixes_count: 3 })).resolves.toBeUndefined();
  });

  /** No learner exists before consent: events wait in the browser, then go once there is a session. */
  it("keeps events from before consent and sends them after", async () => {
    status = 401;
    markFirstOpen(Date.now());
    await track("onboarding_step_completed", { step: "welcome", duration_s: 4 });
    expect(sent).toHaveLength(0);
    status = 202;
    await flushEvents();
    expect(sent.map((e) => e.name)).toEqual(["app_first_open", "onboarding_step_completed"]);
    await flushEvents();
    expect(sent).toHaveLength(2);
  });

  /** Review I2: a browser that used Bolo before tracking began is not a new opener and has no honest first word. */
  it("treats a returning browser as not new", async () => {
    markFirstOpen(Date.now(), true);
    await flushEvents();
    await firstWordSpoken();
    expect(sent).toHaveLength(0);
  });
});
