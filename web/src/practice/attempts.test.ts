import { afterEach, describe, expect, it, vi } from "vitest";
import { attemptProblem, postAttempt } from "./attempts";

describe("practice attempts (P1d)", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("a guest's attempt asks to sign in", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ code: "sign-in" }), { status: 422 })));
    const reply = await postAttempt(new Blob([new Uint8Array(4)]), "Hello.");
    expect(reply).toEqual({ kind: "sign-in" });
    if (reply.kind === "ok") throw new Error("expected a refusal");
    expect(attemptProblem(reply)).toBe("Sign in with Google to practise. It's free.");
  });
});
