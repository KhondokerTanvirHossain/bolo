import { expect, test, type Page } from "./fixtures";

const T = "55555555-5555-5555-5555-555555555555";
const iso = (daysAgo: number) => new Date(Date.now() - daysAgo * 86_400_000).toISOString();
const pts = [{ at: iso(9), level: "BUILDING" }, { at: iso(5), level: "GOOD" }, { at: iso(1), level: "GOOD" }];
const row = (id: string, daysAgo: number, topic: string) =>
  ({ talkId: id, startedAt: iso(daysAgo), minutes: 5, topic, status: "ready", fixes: ["The v sound", "Say it better"] });

type Opts = { audio?: "notKept" | "expired" | "noClips"; deleteStatus?: number };

async function stub(page: Page, sessions = 3, opts: Opts = {}) {
  const events: string[] = [];
  let viewed = 0;
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await page.route("**/api/me", (r) => r.fulfill({ json: { learnerId: "a", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
    account: { email: "r@x.com", name: "R" } } }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null } }));
  await page.route("**/api/events", (r) => { events.push(JSON.parse(r.request().postData() ?? "{}").name); return r.fulfill({ status: 202 }); });
  await page.route("**/api/progress", (r) => r.fulfill({ json: {
    effort: { talks: sessions, minutes: 6 },
    trends: { sessions, after: 3, skills: sessions >= 3 ? { pronunciation: pts, fluency: pts, sentenceBuilding: pts, vocabulary: pts } : {} },
    patterns: { active: [{ key: "V_BH", title: "The v sound", tag: "IMPROVING" }, { key: "filler:um", title: "Says \"um\" often", tag: "STEADY" }], fixed: [] },
    latest: [row(T, 1, "The payment bug demo"), row("t2", 3, "My weekend")] } }));
  await page.route("**/api/history", (r) => r.fulfill({ json: { sessions: [row(T, 1, "The payment bug demo"), row("t2", 3, "My weekend"), row("t3", 40, "First day")] } }));
  let deleted = false;
  await page.route(`**/api/talk/${T}/recording/delete`, (r) => { if (opts.deleteStatus) return r.fulfill({ status: opts.deleteStatus }); deleted = true; return r.fulfill({ status: 204 }); });
  await page.route(`**/api/talk/${T}/debrief/viewed`, (r) => { viewed++; return r.fulfill({ status: 204 }); });
  const noAudio = () => deleted || opts.audio !== undefined;
  await page.route(`**/api/talk/${T}/debrief`, (r) => r.fulfill({ json: {
    status: "ready", topic: "The payment bug demo", recordingDeleted: deleted,
    ...(opts.audio === "notKept" ? { recordingNotKept: true } : opts.audio === "expired" ? { recordingExpired: true } : {}),
    strength: { text: "You explained the bug clearly.", clip: noAudio() ? null : `/api/media/talk/${T}/clip-0.wav` },
    fixes: [{ id: "f1", kind: "sound", pattern: "V_BH", title: "The v sound", example: "v as in van", said: "very good", saidHighlight: "very",
      better: "very good", betterHighlight: "very", tip: "Top teeth on lip.", count: 2, clip: noAudio() ? null : `/api/media/talk/${T}/clip-1.wav`,
      model: noAudio() ? null : `/api/media/talk/${T}/model-1.wav` }],
    skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: false } }));
  await page.route(`**/api/talk/${T}/chat`, (r) => r.fulfill({ json: {
    topic: "The payment bug demo",
    turns: [{ speaker: "bolo", text: "Hi! What did you work on today?" }, { speaker: "learner", text: "I fixed a very old payment bug." },
      { speaker: "bolo", text: "Nice. How did you find it?" }, { speaker: "learner", text: "The logs showed it." }],
    marks: [{ turn: 1, start: 10, end: 14, fix: 1, title: "The v sound" }] } }));
  await page.route("**/api/media/**", (r) => r.fulfill({ status: 200, contentType: "audio/wav", body: Buffer.alloc(44) }));
  await page.route("**/api/practice", (r) => r.fulfill({ json: { patterns: [
    { key: "V_BH", title: "The v sound", example: "v as in van", tip: "Top teeth touch your lower lip for v.", sentences: ["Very few vans visit."] }] } }));
  return { wasDeleted: () => deleted, events, viewedCalls: () => viewed };
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

async function openPastTalk(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await page.getByRole("button", { name: /The payment bug demo/ }).first().click();
  await expect(page.getByText("You explained the bug clearly.")).toBeVisible();
}

test("a talk whose audio the sweep removed says it wasn't kept, with no players and no Delete", async ({ page }) => {
  await stub(page, 3, { audio: "notKept" });
  await openPastTalk(page);
  await expect(page.getByText("Recording not kept, as you chose. Feedback and words are still here.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Delete recording" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /Hear that moment/ })).toHaveCount(0);
});

test("a kept recording past 90 days says so", async ({ page }) => {
  await stub(page, 3, { audio: "expired" });
  await openPastTalk(page);
  await expect(page.getByText("Recordings are kept for 90 days. Feedback and words are still here.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Delete recording" })).toHaveCount(0);
});

test("a talk with no clips but no flag doesn't claim the learner chose it", async ({ page }) => {
  await stub(page, 3, { audio: "noClips" });
  await openPastTalk(page);
  await expect(page.getByText(/Recording not kept/)).toHaveCount(0);
});

test("reopening a past talk doesn't count as a new debrief view", async ({ page }) => {
  const s = await stub(page);
  await openPastTalk(page);
  await page.waitForTimeout(500);
  expect(s.events).not.toContain("debrief_viewed");
  expect(s.viewedCalls()).toBe(0);
});

test("a failed delete keeps the sheet open and says so", async ({ page }) => {
  await stub(page, 3, { deleteStatus: 500 });
  await openPastTalk(page);
  await page.getByRole("button", { name: "Delete recording" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Delete recording" }).click();
  await expect(page.getByRole("dialog").getByText("Couldn't delete it. Try again.")).toBeVisible();
});

test("my patterns stay hidden until 3 talks", async ({ page }) => {
  await stub(page, 1);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await expect(page.getByText("1 of 3")).toBeVisible();
  await expect(page.getByRole("heading", { name: "My patterns" })).toHaveCount(0);
});

test("on a phone, a past talk's Back and Delete recording are on screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page);
  await openPastTalk(page);
  await expect(page.getByRole("button", { name: "Back", exact: true })).toBeInViewport();
  await expect(page.getByRole("button", { name: "Delete recording" })).toBeInViewport();
});

test("a past talk shows its full chat, and a marked spot opens its fix", async ({ page }) => {
  await stub(page);
  await openPastTalk(page);
  await page.getByRole("button", { name: "See full chat" }).click();
  await expect(page.getByRole("heading", { name: "Your talk" })).toBeVisible();
  await expect(page.getByText("Hi! What did you work on today?")).toBeVisible();
  await expect(page.getByText("The logs showed it.")).toBeVisible();
  await page.getByRole("button", { name: '"very": The v sound' }).click();
  await expect(page.getByText("Top teeth on lip.")).toBeVisible();
});

test("on a phone, the full chat fits with no sideways scroll", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page);
  await openPastTalk(page);
  await page.getByRole("button", { name: "See full chat" }).click();
  await expect(page.getByText("The logs showed it.")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("on a phone, a mark far down a long chat opens its card at the top", async ({ page }) => {
  // A short screen (landscape, or the keyboard up): the fix card is taller than the pane, so an old scroll would stick.
  await page.setViewportSize({ width: 390, height: 420 });
  await stub(page);
  const turns = Array.from({ length: 60 }, (_, i) => i % 2 === 0
    ? { speaker: "bolo", text: `Question number ${i / 2 + 1}, tell me a bit more about that, please?` }
    : { speaker: "learner", text: `This is my answer ${i}, and it was a very long day at work.` });
  await page.route(`**/api/talk/${T}/chat`, (r) => r.fulfill({ json: { topic: "A long talk", turns,
    marks: [{ turn: 59, start: 35, end: 39, fix: 1, title: "The v sound" }] } }));
  await openPastTalk(page);
  await page.getByRole("button", { name: "See full chat" }).click();
  const mark = page.getByRole("button", { name: '"very": The v sound' });
  await mark.scrollIntoViewIfNeeded();
  await mark.click();
  await expect(page.getByText("Top teeth on lip.")).toBeVisible();
  expect(await page.locator(".dk-deb__pane").evaluate((e) => e.scrollTop)).toBe(0);
});

test("arrow keys don't move the hidden cards while the chat is open", async ({ page }) => {
  await stub(page);
  await openPastTalk(page);
  await page.getByRole("button", { name: "See full chat" }).click();
  await expect(page.getByText("The logs showed it.")).toBeVisible();
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowDown");
  await page.getByRole("button", { name: "Back to feedback" }).click();
  await expect(page.getByText("You explained the bug clearly.")).toBeVisible();
});

test("Esc leaves a past talk's feedback, and closes the chat first", async ({ page }) => {
  await stub(page);
  await openPastTalk(page);
  await page.getByRole("button", { name: "See full chat" }).click();
  await expect(page.getByText("The logs showed it.")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByText("You explained the bug clearly.")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("heading", { level: 1, name: "Progress" })).toBeVisible();
});
