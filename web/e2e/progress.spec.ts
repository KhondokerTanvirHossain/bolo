import { expect, test, type Page } from "@playwright/test";

const T = "55555555-5555-5555-5555-555555555555";
const iso = (daysAgo: number) => new Date(Date.now() - daysAgo * 86_400_000).toISOString();
const pts = [{ at: iso(9), level: "BUILDING" }, { at: iso(5), level: "GOOD" }, { at: iso(1), level: "GOOD" }];
const row = (id: string, daysAgo: number, topic: string) =>
  ({ talkId: id, startedAt: iso(daysAgo), minutes: 5, topic, status: "ready", fixes: ["The v sound", "Say it better"] });

async function stub(page: Page, sessions = 3) {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await page.route("**/api/me", (r) => r.fulfill({ json: { learnerId: "a", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
    account: { email: "r@x.com", name: "R" } } }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/progress", (r) => r.fulfill({ json: {
    effort: { talks: sessions, minutes: 6 },
    trends: { sessions, after: 3, skills: sessions >= 3 ? { pronunciation: pts, fluency: pts, sentenceBuilding: pts, vocabulary: pts } : {} },
    patterns: { active: [{ key: "V_BH", title: "The v sound", tag: "IMPROVING" }, { key: "filler:um", title: "Says \"um\" often", tag: "STEADY" }], fixed: [] },
    latest: [row(T, 1, "The payment bug demo"), row("t2", 3, "My weekend")] } }));
  await page.route("**/api/history", (r) => r.fulfill({ json: { sessions: [row(T, 1, "The payment bug demo"), row("t2", 3, "My weekend"), row("t3", 40, "First day")] } }));
  let deleted = false;
  await page.route(`**/api/talk/${T}/recording/delete`, (r) => { deleted = true; return r.fulfill({ status: 204 }); });
  await page.route(`**/api/talk/${T}/debrief/viewed`, (r) => r.fulfill({ status: 204 }));
  await page.route(`**/api/talk/${T}/debrief`, (r) => r.fulfill({ json: {
    status: "ready", topic: "The payment bug demo", recordingDeleted: deleted,
    strength: { text: "You explained the bug clearly.", clip: deleted ? null : `/api/media/talk/${T}/clip-0.wav` },
    fixes: [{ id: "f1", kind: "sound", pattern: "V_BH", title: "The v sound", example: "v as in van", said: "very good", saidHighlight: "very",
      better: "very good", betterHighlight: "very", tip: "Top teeth on lip.", count: 2, clip: deleted ? null : `/api/media/talk/${T}/clip-1.wav`,
      model: deleted ? null : `/api/media/talk/${T}/model-1.wav` }],
    skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: false } }));
  await page.route("**/api/media/**", (r) => r.fulfill({ status: 200, contentType: "audio/wav", body: Buffer.alloc(44) }));
  await page.route("**/api/practice", (r) => r.fulfill({ json: { patterns: [
    { key: "V_BH", title: "The v sound", example: "v as in van", tip: "Top teeth touch your lower lip for v.", sentences: ["Very few vans visit."] }] } }));
  return { wasDeleted: () => deleted };
}

test("Progress shows effort, skills in words, my patterns and the latest talks", async ({ page }) => {
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Progress" })).toBeVisible();
  await expect(page.getByText("3 talks · 6 minutes of speaking")).toBeVisible();
  await expect(page.locator(".bo-trend__level", { hasText: "Good" })).toHaveCount(4);
  await expect(page.getByText("Improving")).toBeVisible();
  await expect(page.getByText(/The payment bug demo/).first()).toBeVisible();
});

test("fewer than 3 talks shows when trends will appear", async ({ page }) => {
  await stub(page, 1);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await expect(page.getByText("Your trends appear after 3 sessions")).toBeVisible();
  await expect(page.getByText("1 of 3")).toBeVisible();
});

test("History groups by week, and a past talk can delete its recording", async ({ page }) => {
  const s = await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await page.getByRole("button", { name: "See all" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "History" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "This week" }).or(page.getByRole("heading", { level: 2, name: "Last week" })).first()).toBeVisible();
  await page.getByRole("button", { name: /The payment bug demo/ }).click();
  await expect(page.getByText("You explained the bug clearly.")).toBeVisible();
  await page.getByRole("button", { name: "Delete recording" }).click();
  await expect(page.getByRole("heading", { name: "Delete this recording?" })).toBeVisible();
  await page.getByRole("dialog").getByRole("button", { name: "Delete recording" }).click();
  await expect.poll(() => s.wasDeleted()).toBe(true);
  await expect(page.getByText("Recording deleted. Feedback and words are still here.")).toBeVisible();
});

test("Practise these opens practice on the talk's sound", async ({ page }) => {
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await page.getByRole("button", { name: /The payment bug demo/ }).first().click();
  await expect(page.getByText("You explained the bug clearly.")).toBeVisible();
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowDown");
  await page.getByRole("button", { name: "Practise these (3 min)" }).click();
  await expect(page.getByText("Very few vans visit.")).toBeVisible();
});

test("on a phone, Progress fits with no sideways scroll", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Progress" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  // The content itself must fit, not only the page: the top bar and the "See all" link stay inside the screen.
  for (const el of [page.getByRole("button", { name: "See all" }), page.getByRole("button", { name: "Me", exact: true })]) {
    const b = (await el.boundingBox())!;
    expect(b.x + b.width).toBeLessThanOrEqual(390);
  }
});
