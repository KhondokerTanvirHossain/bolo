import { afterEach, describe, expect, it } from "vitest";

describe("clipStore", () => {
  afterEach(() => { delete (globalThis as { indexedDB?: unknown }).indexedDB; });

  it("never blocks on an IndexedDB that hangs (a known Safari bug)", async () => {
    (globalThis as { indexedDB?: unknown }).indexedDB = { open: () => ({}) }; // never calls back
    const { loadClip, saveClip } = await import("./clipStore");
    const saved = await Promise.race([saveClip("k", new Blob(["x"])).then(() => "saved"),
      new Promise((r) => setTimeout(() => r("hung"), 300))]);
    expect(saved).toBe("saved");
    const loaded = await Promise.race([loadClip("k"), new Promise((r) => setTimeout(() => r("hung"), 3000))]);
    expect(loaded).toBeInstanceOf(Blob);
  });
});

describe("clipStore forgetting (sign-out, delete everything)", () => {
  it("deleting by prefix forgets the clips held in memory too", async () => {
    const { deleteClips, loadClip, saveClip } = await import("./clipStore");
    await saveClip("intro-learnerA-7", new Blob(["a"]));
    await saveClip("day1", new Blob(["d"]));
    await deleteClips("intro-");
    expect(await loadClip("intro-learnerA-7")).toBeNull();
    expect(await loadClip("day1")).toBeInstanceOf(Blob);
  });
});
