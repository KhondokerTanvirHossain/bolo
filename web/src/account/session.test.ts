// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const clips = new Map<string, Blob>();
vi.mock("@/audio/clipStore", () => ({
  loadClip: vi.fn(async (k: string) => clips.get(k) ?? null),
  saveClip: vi.fn(async (k: string, b: Blob) => { clips.set(k, b); }),
  deleteClip: vi.fn(async (k: string) => { clips.delete(k); }),
  deleteClips: vi.fn(async (prefix: string) => { for (const k of [...clips.keys()]) if (k.startsWith(prefix)) clips.delete(k); }),
}));

import { signOut, uploadDay1 } from "./session";
import type { LearnerView } from "@/api/browser";

const keepMe = { learnerId: "a", kind: "account", consent: { keepRecordings: true, policyVersion: "v" }, profile: null } as LearnerView;
let serverHasClip = false;
const puts: string[] = [];

beforeEach(() => {
  window.localStorage.clear();
  clips.clear();
  clips.set("day1", new Blob(["x"], { type: "audio/webm" }));
  puts.length = 0;
  serverHasClip = false;
  window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk", keepRecordings: true }));
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/me/day1" && (!init || !init.method || init.method === "GET")) return new Response(null, { status: serverHasClip ? 200 : 404 });
    if (url === "/api/me/day1" && init?.method === "PUT") { puts.push(url); return new Response(null, { status: 204 }); }
    return new Response(null, { status: 204 });
  }));
});
afterEach(() => vi.unstubAllGlobals());

describe("the Day 1 clip after sign-in", () => {
  it("is uploaded when the account has none and this browser chose to keep recordings", async () => {
    await uploadDay1(keepMe);
    expect(puts).toHaveLength(1);
  });

  /** Review I1: a new laptop's clip must never replace the account's real Day 1 voice. */
  it("never replaces a clip the account already has", async () => {
    serverHasClip = true;
    await uploadDay1(keepMe);
    expect(puts).toHaveLength(0);
  });

  /** Review I1: the choice made on this browser is the one that counts for this clip. */
  it("stays on the device when this browser chose not to keep recordings", async () => {
    window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk", keepRecordings: false }));
    await uploadDay1(keepMe);
    expect(puts).toHaveLength(0);
  });
});

describe("sign-out", () => {
  /** Review I2/I3: nothing of the person stays behind, and the next visit starts at the welcome screen. */
  it("clears this browser's Bolo state and the Day 1 clip", async () => {
    window.localStorage.setItem("bolo.loop.v1", "{}");
    window.localStorage.setItem("bolo.captions", "off");
    clips.set("intro-a1-7", new Blob(["x"]));
    window.localStorage.setItem("bolo.drillQueue", "[]");
    await signOut();
    expect(window.localStorage.getItem("bolo.onboarding.v1")).toBeNull();
    expect(window.localStorage.getItem("bolo.loop.v1")).toBeNull();
    expect(window.localStorage.getItem("bolo.captions")).toBeNull();
    expect(clips.has("day1")).toBe(false);
    expect(clips.has("intro-a1-7")).toBe(false);
    expect(window.localStorage.getItem("bolo.drillQueue")).toBeNull();
  });
});
