// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";

vi.mock("@/audio/clipStore", () => ({ loadClip: vi.fn(async () => null), saveClip: vi.fn(), deleteClip: vi.fn(), deleteClips: vi.fn() }));
vi.mock("./firebase", () => ({ googleIdToken: vi.fn(async () => ({ kind: "token", idToken: "tok" })) }));

import { signInWithGoogle } from "./session";
import { takeRefFromUrl } from "@/referral/referral";

afterEach(() => { vi.unstubAllGlobals(); window.sessionStorage.clear(); });

/** P2d: a friend's ?ref= code goes with Google sign-in, so a new account is counted for whoever invited them. */
it("sends the ref code from the link with Google sign-in", async () => {
  window.history.replaceState(null, "", "/?ref=ab12cd34");
  takeRefFromUrl();
  const bodies: string[] = [];
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/auth/config") return Response.json({ enabled: true, apiKey: "k", authDomain: "d", projectId: "p" });
    if (url === "/api/auth/google") { bodies.push(String(init?.body)); return Response.json({ learnerId: "a", kind: "account", consent: null, profile: null }); }
    return new Response(null, { status: 204 });
  }));
  const r = await signInWithGoogle();
  expect(r.kind).toBe("ok");
  expect(JSON.parse(bodies[0])).toMatchObject({ idToken: "tok", ref: "ab12cd34" });
});
