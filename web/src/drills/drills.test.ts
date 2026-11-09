// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { hideKeyWords, loadQueue, queueRest, skipDrill, summaryLines, withQueue, finishDrill, type Drill } from "./drills";

const pairs: Drill = { key: "pairs:V_BH", kind: "pairs", title: "The v sound", pattern: "V_BH", tip: "t", listen: [], say: [] };
const better: Drill = { key: "sayBetter:i went", kind: "sayBetter", title: "Say it better", said: "I go", better: "I went to the market.", keyWords: ["went", "the"] };
const shadow: Drill = { key: "shadow:it was", kind: "shadow", title: "Shadow it", sentence: "It was really good." };

describe("drills (DRL-1…7)", () => {
  beforeEach(() => window.localStorage.clear());

  it("queueRetiresAfterThreeSkips", () => {
    skipDrill(pairs, "t1");
    skipDrill(pairs, "t1");
    expect(loadQueue()).toHaveLength(1);
    expect(loadQueue()[0].skips).toBe(2);
    skipDrill(pairs, "t1");
    expect(loadQueue()).toHaveLength(0);
  });

  it("queuedDrillsComeFirstWithinThree", () => {
    skipDrill(shadow, "old");
    const run = withQueue([pairs, better, { ...shadow, key: "shadow:new" }], "t2");
    expect(run.map((d) => d.drill.key)).toEqual(["shadow:it was", "pairs:V_BH", "sayBetter:i went"]);
    expect(run[0].talkId).toBe("old");
    expect(run[1].talkId).toBe("t2");
  });

  it("a finished drill leaves the queue; closing queues the rest", () => {
    skipDrill(pairs, "t1");
    finishDrill(pairs);
    expect(loadQueue()).toHaveLength(0);
    queueRest([{ drill: better, talkId: "t1" }, { drill: shadow, talkId: "t1" }]);
    expect(loadQueue().map((q) => q.drill.key)).toEqual(["sayBetter:i went", "shadow:it was"]);
  });

  it("summaryShowsMovementOnly", () => {
    expect(summaryLines([
      { title: "The v sound", first: "CLOSER", best: "NAILED" },
      { title: "Say it better", first: "ONCE_MORE", best: "CLOSER" },
      { title: "Shadow it", first: "CLOSER", best: "CLOSER" },
      { title: "Longer answers", skipped: true },
    ])).toEqual([
      "The v sound: Closer → Nailed it",
      "Say it better: Once more → Closer",
      "Shadow it: Closer",
      "Longer answers: Skipped. It comes back next time.",
    ]);
  });

  it("sayBetterHidesKeyWordsThenAll", () => {
    expect(hideKeyWords("I went to the market.", ["went", "the"], 1)).toBe("I went to the market.");
    expect(hideKeyWords("I went to the market.", ["went", "the"], 2)).toBe("I ____ to ____ market.");
    expect(hideKeyWords("I went to the market.", ["went", "the"], 3)).toBeNull();
  });
});
