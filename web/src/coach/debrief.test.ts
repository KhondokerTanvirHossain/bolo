import { describe, expect, it } from "vitest";
import { pollDebrief, type Debrief } from "./debrief";

function clock() {
  let t = 0;
  return { now: () => t, sleep: async (ms: number) => { t += ms; } };
}

describe("pollDebrief", () => {
  it("polls while scoring and stops when ready", async () => {
    const answers: (Debrief | null)[] = [{ status: "scoring" }, { status: "scoring" }, { status: "ready", fixes: [] }];
    const seen: string[] = [];
    const c = clock();
    const final = await pollDebrief({ fetch: async () => answers.shift() ?? null, ...c }, (d) => seen.push(d.status));
    expect(final?.status).toBe("ready");
    expect(seen).toEqual(["scoring", "scoring", "ready"]);
  });

  it("stops on too_short and on failed", async () => {
    for (const status of ["too_short", "failed"] as const) {
      const c = clock();
      expect((await pollDebrief({ fetch: async () => ({ status }), ...c }, () => {}))?.status).toBe(status);
    }
  });

  it("gives up as failed after 45 s of scoring", async () => {
    const c = clock();
    const final = await pollDebrief({ fetch: async () => ({ status: "scoring" }), ...c }, () => {});
    expect(final?.status).toBe("failed");
    expect(c.now()).toBeGreaterThanOrEqual(45_000);
  });

  it("no debrief at all (404) is null", async () => {
    const c = clock();
    expect(await pollDebrief({ fetch: async () => null, ...c }, () => {})).toBeNull();
  });
});
