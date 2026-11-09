import { expect, test, type Page } from "./fixtures";

test.beforeEach(async ({ page, browserName }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" }));
  });
  if (browserName !== "firefox") return;
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    md.getUserMedia = () => original({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
  });
});

const PRACTICE = { patterns: [
  { key: "S_CLUSTER_VOWEL", title: "The s at the start", example: "s as in school", tip: "Start straight on the s: school, not ischool.",
    sentences: ["I study at a small school near the station.", "Speak slowly and stay calm.", "The street was full of students."] },
  { key: "V_BH", title: "The v sound", example: "v as in van", tip: "Top teeth touch your lower lip for v.",
    sentences: ["It is very important to visit the village.", "I have a van and a vest.", "We moved to a very lively avenue."] },
] };

async function stub(page: Page, outcome: object) {
  const learner = { learnerId: "1", kind: "guest", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 } };
  await page.route("**/api/me", (r) => r.fulfill({ json: learner }));
  await page.route("**/api/practice", (r) => r.fulfill({ json: PRACTICE }));
  const posts: string[] = [];
  await page.route("**/api/attempts", async (r) => {
    posts.push(r.request().headers()["content-type"] ?? "");
    await new Promise((res) => setTimeout(res, 300));
    await r.fulfill({ json: outcome });
  });
  return { posts };
}

test("practise a sound: say the sentence, see what to work on", async ({ page }) => {
  const s = await stub(page, { level: "CLOSER", heard: true, focus: [
    { word: "school", kind: "EXTRA_VOWEL", title: "The s at the start", tip: "Start straight on the s: school, not ischool." }] });
  await page.goto("/");
  await page.getByRole("button", { name: "Practise sounds" }).click();
  await page.getByRole("button", { name: /The s at the start/ }).click();
  await expect(page.getByText("I study at a small school near the station.")).toBeVisible();
  await page.getByRole("button", { name: "Start recording" }).click();
  await page.waitForTimeout(800);
  await page.getByRole("button", { name: "Stop recording" }).click();
  await expect(page.getByText("Closer")).toBeVisible();
  await expect(page.locator(".bo-said", { hasText: "school" })).toBeVisible();
  await expect(page.getByText("Start straight on the s: school, not ischool.").last()).toBeVisible();
  expect(s.posts[0]).toContain("multipart/form-data");
  await page.getByRole("button", { name: "Next sentence" }).click();
  await expect(page.getByText("Speak slowly and stay calm.")).toBeVisible();
});

test("nothing heard asks kindly for one more go", async ({ page }) => {
  await stub(page, { level: "ONCE_MORE", heard: false, focus: [] });
  await page.goto("/");
  await page.getByRole("button", { name: "Practise sounds" }).click();
  await page.getByRole("button", { name: /The v sound/ }).click();
  await page.getByRole("button", { name: "Start recording" }).click();
  await page.waitForTimeout(500);
  await page.getByRole("button", { name: "Stop recording" }).click();
  await expect(page.getByText("We couldn't hear that. Let's try once more.")).toBeVisible();
});

/** Founder test 2026-11-08: the practice card sat off to the bottom right, cut off, on a blank page. */
test("the practice card sits whole in the middle of the screen, over Bolo's scene", async ({ page }) => {
  await stub(page, { level: "NAILED", heard: true, focus: [] });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/");
  await page.getByRole("button", { name: "Practise sounds" }).click();
  const card = page.locator("[data-screen=drill] .dk-card");
  await expect(card.getByRole("heading", { name: "Practise sounds" })).toBeVisible();
  const box = (await card.boundingBox())!;
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(1440);
  expect(box.y + box.height).toBeLessThanOrEqual(900);
  expect(Math.abs(box.x + box.width / 2 - 720)).toBeLessThan(4);
  await expect(page.locator("[data-screen=drill] .bo-scene")).toBeAttached();
});

/** Every card is fixed-width; on a phone it must fit with a 16 px gutter, the sentence view included. */
test("on a phone the practice card fits the screen", async ({ page }) => {
  await stub(page, { level: "NAILED", heard: true, focus: [] });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByRole("button", { name: "Practise sounds" }).click();
  const card = page.locator("[data-screen=drill] .dk-card");
  for (const step of ["list", "sentence"]) {
    if (step === "sentence") await page.getByRole("button", { name: /The s at the start/ }).click();
    await expect(card).toBeVisible();
    const box = (await card.boundingBox())!;
    expect(box.x, step).toBeGreaterThanOrEqual(15);
    expect(box.x + box.width, step).toBeLessThanOrEqual(375);
  }
});

test("the practice card closes with its ✕ and with Esc", async ({ page }) => {
  await stub(page, { level: "NAILED", heard: true, focus: [] });
  await page.goto("/");
  await page.getByRole("button", { name: "Practise sounds" }).click();
  await page.getByRole("button", { name: /The v sound/ }).click();
  await expect(page.getByText("It is very important to visit the village.")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
  await page.getByRole("button", { name: "Practise sounds" }).click();
  await page.getByRole("button", { name: /The v sound/ }).click();
  await page.getByRole("button", { name: "Close" }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});

test("the practice card has one filled button", async ({ page }) => {
  await stub(page, { level: "NAILED", heard: true, focus: [] });
  await page.goto("/");
  await page.getByRole("button", { name: "Practise sounds" }).click();
  await page.getByRole("button", { name: /The v sound/ }).click();
  await expect(page.locator('[data-screen="drill"] .bo-btn--primary')).toHaveCount(1);
});
