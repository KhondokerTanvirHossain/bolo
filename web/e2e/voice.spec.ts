import { expect, test, type Page } from "./fixtures";

test.beforeEach(async ({ page, browserName }) => {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  if (browserName !== "firefox") return;
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    md.getUserMedia = () => original({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
  });
});

const dhaka = (offsetDays: number) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Dhaka" }).format(new Date(Date.now() + offsetDays * 86_400_000));
const PROGRESS = { effort: { talks: 14, minutes: 70 }, latest: [], patterns: { active: [], fixed: [] },
  trends: { sessions: 5, after: 3, skills: { fluency: [{ at: "2026-11-04T10:00:00Z", level: "BUILDING" }, { at: "2026-11-09T10:00:00Z", level: "GOOD" }] } } };

async function stub(page: Page, { keep = true, day1Here = true, recorded = false, due = 7 as number | null, failFirst = false,
  serverRefusesAudio = false } = {}) {
  const me = { learnerId: "a1", kind: "account", consent: { keepRecordings: keep, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
    account: { email: "r@x.com", name: "Rafi" } };
  const state = { recorded, posts: [] as string[] };
  await page.route("**/api/me", (r) => r.fulfill({ json: me }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: state.recorded || due === null ? null : { kind: "intro", day: 7 } } }));
  await page.route("**/api/progress", (r) => r.fulfill({ json: PROGRESS }));
  await page.route("**/api/me/voice", (r) => r.fulfill({ json: { day1: dhaka(-6), unlocked: due === null ? [] : [7], due: state.recorded ? null : due,
    nextDay: due === null ? 7 : 14, nextOn: due === null ? dhaka(3) : dhaka(7),
    recordings: state.recorded ? [{ day: 7, recordedAt: new Date().toISOString(), onServer: keep }] : [] } }));
  await page.route("**/api/me/intro/7", async (r) => {
    if (r.request().method() === "POST") {
      const body = r.request().postDataBuffer()?.toString("latin1") ?? "";
      state.posts.push(body);
      if (failFirst && state.posts.length === 1) return r.fulfill({ status: 500 });
      if (serverRefusesAudio && body.includes('name="audio"')) return r.fulfill({ status: 422, json: { code: "not-kept" } });
      state.recorded = true;
      return r.fulfill({ status: 204 });
    }
    return r.fulfill({ status: 200, contentType: "audio/webm", body: Buffer.alloc(64) });
  });
  await page.route("**/api/me/day1", (r) => r.fulfill({ status: 404 }));
  if (day1Here) {
    await page.addInitScript(() => {
      const req = indexedDB.open("bolo", 1);
      req.onupgradeneeded = () => req.result.createObjectStore("clips");
      req.onsuccess = () => { req.result.transaction("clips", "readwrite").objectStore("clips").put(new Blob([new Uint8Array(8)], { type: "audio/webm" }), "day1"); };
    });
  }
  return state;
}

async function recordIntro(page: Page) {
  await page.getByRole("button", { name: /Record your intro again/ }).click();
  await expect(page.getByRole("heading", { name: "Introduce yourself again, like on Day 1" })).toBeVisible();
  await page.getByRole("button", { name: "Start recording" }).click();
  await expect(page.getByRole("button", { name: "Stop recording" })).toBeVisible();
  await page.waitForTimeout(1500);
  await page.keyboard.press("Space");
  await page.getByRole("button", { name: "Keep it" }).click();
}

test("Day 7: Home offers the intro again, and Progress plays Day 1 and Day 7 side by side", async ({ page }) => {
  const s = await stub(page);
  await page.goto("/");
  await recordIntro(page);
  await expect(page.getByText("Saved. Here's how far you've come.")).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "Your voice, then and now" })).toBeVisible({ timeout: 10_000 });
  await expect(page.getByRole("button", { name: "Play your Day 1 intro" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Play your Day 7 intro" })).toBeVisible();
  await expect(page.getByText("Your fluency went from Building to Good.")).toBeVisible();
  expect(s.posts[0]).toContain('name="audio"');
});

test("not kept: the intro stays in this browser and only the date is sent", async ({ page }) => {
  const s = await stub(page, { keep: false });
  await page.goto("/");
  await recordIntro(page);
  await expect(page.getByRole("button", { name: "Play your Day 7 intro" })).toBeVisible({ timeout: 10_000 });
  expect(s.posts[0]).not.toContain('name="audio"');
  expect(s.posts[0]).toContain('name="kept"');
});

test("without the Day 1 clip, the new intro is the starting point", async ({ page }) => {
  await stub(page, { day1Here: false, recorded: true });
  await page.goto("/");
  await page.getByRole("button", { name: "Progress", exact: true }).first().click();
  await expect(page.getByText("Your Day 1 clip isn't here, so this recording is your new starting point.")).toBeVisible();
});

test("between unlocks, Progress plays Day 1 and says when the next one opens", async ({ page }) => {
  await stub(page, { due: null });
  await page.goto("/");
  await page.getByRole("button", { name: "Progress", exact: true }).first().click();
  await expect(page.getByRole("button", { name: "Play your Day 1 intro" })).toBeVisible();
  await expect(page.getByText("Day 7 unlocks in 3 days")).toBeVisible();
});

test("on a phone, the voice compare fits with no sideways scroll", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page, { recorded: true });
  await page.goto("/");
  await page.getByRole("button", { name: "Progress", exact: true }).first().click();
  await expect(page.getByRole("button", { name: "Play your Day 7 intro" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("a failed save says so, and Try again sends the same clip", async ({ page }) => {
  const s = await stub(page, { failFirst: true });
  await page.goto("/");
  await recordIntro(page);
  await expect(page.getByRole("heading", { name: "That didn't save" })).toBeVisible();
  await expect(page.getByText("Saved. Here's how far you've come.")).toHaveCount(0);
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByText("Saved. Here's how far you've come.")).toBeVisible();
  expect(s.posts).toHaveLength(2);
  expect(s.posts[1]).toContain('name="audio"');
});

test("if keeping was switched off since, the intro is noted by date and stays here", async ({ page }) => {
  const s = await stub(page, { serverRefusesAudio: true });
  await page.goto("/");
  await recordIntro(page);
  await expect(page.getByText("Saved. Here's how far you've come.")).toBeVisible();
  expect(s.posts).toHaveLength(2);
  expect(s.posts[1]).toContain('name="kept"');
});
