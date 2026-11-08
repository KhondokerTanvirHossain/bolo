// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { keepSlower } from "./settings";

let me: number;
const puts: unknown[] = [];

beforeEach(() => {
  window.localStorage.clear();
  puts.length = 0;
  me = 200;
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/me") {
      return me === 200
        ? new Response(JSON.stringify({ learnerId: "1", kind: "guest", consent: null, profile: null,
            settings: { buddySpeed: "normal", captions: false } }), { status: 200 })
        : new Response(null, { status: me });
    }
    puts.push(JSON.parse(String(init?.body)));
    return new Response(null, { status: 204 });
  }));
});
afterEach(() => vi.unstubAllGlobals());

describe("keepSlower", () => {
  /** Review I4: keeping Bolo slower must not change captions, on the server or on this device. */
  it("keeps the saved captions and this device's choice", async () => {
    window.localStorage.setItem("bolo.captions", "on");
    await keepSlower();
    expect(puts).toEqual([{ buddySpeed: "slower", captions: false }]);
    expect(window.localStorage.getItem("bolo.captions")).toBe("on");
  });

  it("saves nothing when the current settings can't be read", async () => {
    me = 503;
    expect(await keepSlower()).toBe(false);
    expect(puts).toHaveLength(0);
  });
});
