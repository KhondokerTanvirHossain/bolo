// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { hideKeyWords, loadQueue, queueRest, skipDrill, summaryLines, withQueue, finishDrill, type Drill } from "./drills";

const pairs: Drill = { key: "pairs:V_BH", kind: "pairs", title: "The v sound", pattern: "V_BH", tip: "t", listen: [], say: [] };
const better: Drill = { key: "sayBetter:i went", kind: "sayBetter", title: "Say it better", said: "I go", better: "I went to the market.", keyWords: ["went", "the"] };
const shadow: Drill = { key: "shadow:it was", kind: "shadow", title: "Shadow it", sentence: "It was really good." };
const A = "learnerA";

describe("drills (DRL-1…7)", () => {
  beforeEach(() => window.localStorage.clear());

  it("queueRetiresAfterThreeSkips", () => {
    skipDrill(pairs, "t1", A);
    skipDrill(pairs, "t1", A);
    expect(loadQueue(A)).toHaveLength(1);
    expect(loadQueue(A)[0].skips).toBe(2);
    skipDrill(pairs, "t1", A);
    expect(loadQueue(A)).toHaveLength(0);
  });

  it("the queue is each learner's own (a shared browser)", () => {
    skipDrill(pairs, "t1", A);
    expect(loadQueue("learnerB")).toHaveLength(0);
    expect(withQueue([shadow], "t9", "learnerB").map((p) => p.drill.key)).toEqual(["shadow:it was"]);
  });

  it("queued drills come first, but the talk you chose keeps a place", () => {
    skipDrill(shadow, "old", A);
    skipDrill({ ...shadow, key: "shadow:2" }, "old", A);
    skipDrill({ ...shadow, key: "shadow:3" }, "old", A);
    const run = withQueue([pairs, better], "t2", A);
    expect(run.map((d) => d.drill.key)).toEqual(["shadow:it was", "shadow:2", "pairs:V_BH"]);
    expect(run[2].talkId).toBe("t2");
  });

  it("a finished drill leaves the queue; closing queues the rest and counts like a skip", () => {
    skipDrill(pairs, "t1", A);
    finishDrill(pairs, A);
    expect(loadQueue(A)).toHaveLength(0);
    queueRest([{ drill: better, talkId: "t1" }], A);
    queueRest([{ drill: better, talkId: "t1" }], A);
    expect(loadQueue(A)[0].skips).toBe(2);
    queueRest([{ drill: better, talkId: "t1" }], A);
    expect(loadQueue(A)).toHaveLength(0);
  });

  it("summaryShowsMovementOnly", () => {
    expect(summaryLines([
      { title: "The v sound", first: "CLOSER", best: "NAILED" },
      { title: "Say it better", first: "ONCE_MORE", best: "CLOSER" },
      { title: "Shadow it", first: "CLOSER", best: "CLOSER" },
      { title: "Shadow it", first: "ONCE_MORE", best: "ONCE_MORE" },
      { title: "Longer answers", skipped: true },
      { title: "The w sound", skipped: true, retired: true },
    ])).toEqual([
      "The v sound: Closer → Nailed it",
      "Say it better: Once more → Closer",
      "Shadow it: Closer",
      "Shadow it: Good effort. It comes back next time.",
      "Longer answers: Skipped. It comes back next time.",
      "The w sound: Skipped.",
    ]);
  });

  it("sayBetterHidesKeyWordsThenAll", () => {
    expect(hideKeyWords("I went to the market.", ["went", "the"], 1)).toBe("I went to the market.");
    expect(hideKeyWords("I went to the market.", ["went", "the"], 2)).toBe("I ____ to ____ market.");
    expect(hideKeyWords("I went to the market.", ["went", "the"], 3)).toBeNull();
  });
});
