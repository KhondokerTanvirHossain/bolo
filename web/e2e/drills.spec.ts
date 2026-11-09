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

const T = "66666666-6666-6666-6666-666666666666";
const PAIRS = { key: "pairs:V_BH", kind: "pairs", title: "The v sound", pattern: "V_BH", tip: "Top teeth touch your lower lip for v.",
  listen: [{ a: "very", b: "berry", target: "very" }], say: ["voice"] };
const SAY_BETTER = { key: "sayBetter:i went", kind: "sayBetter", title: "Say it better", said: "I go market", better: "I went to the market.", keyWords: ["went"] };
const SHADOW = { key: "shadow:it was", kind: "shadow", title: "Shadow it", sentence: "It was really good." };

async function stub(page: Page, drills: object[], level = "NAILED") {
  const said: string[] = [];
  await page.route("**/api/me", (r) => r.fulfill({ json: { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
    account: { email: "r@x.com", name: "Rafi" } } }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: { kind: "practice", pattern: "V_BH", title: "The v sound", talkId: T } } }));
  await page.route(`**/api/talk/${T}/drills`, (r) => r.fulfill({ json: { drills } }));
  await page.route(`**/api/talk/${T}/drills/say**`, (r) => { said.push(new URL(r.request().url()).searchParams.get("text") ?? ""); return r.fulfill({ status: 200, contentType: "audio/wav", body: Buffer.alloc(44) }); });
  await page.route("**/api/attempts", (r) => r.fulfill({ json: { level, heard: true, focus: [] } }));
  return { said };
}

async function say(page: Page, button: string) {
  await page.getByRole("button", { name: button, exact: true }).click();
  await expect(page.getByRole("button", { name: "Stop recording" })).toBeVisible();
  await page.waitForTimeout(600);
  await page.getByRole("button", { name: "Stop recording" }).click();
}

test("Home's practice card opens the drills: hear the difference, say the word, then what moved", async ({ page }) => {
  const s = await stub(page, [PAIRS]);
  await page.goto("/");
  await page.getByRole("button", { name: /Practise the v sound/ }).click();
  await expect(page.getByRole("heading", { name: "Which did you hear?" })).toBeVisible();
  await expect.poll(() => s.said.length).toBeGreaterThan(0);
  await page.getByRole("button", { name: new RegExp(`^${s.said[0]}`) }).click();
  await expect(page.getByText("That's it.")).toBeVisible();
  await expect(page.getByText("voice", { exact: true })).toBeVisible();
  await say(page, "Try it");
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByText("Nice work.")).toBeVisible();
  await expect(page.getByText("The v sound: Nailed it")).toBeVisible();
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Ready when you are." })).toBeVisible();
});

test("three tries without Nailed it: Good effort, and it moves on", async ({ page }) => {
  await stub(page, [SHADOW], "ONCE_MORE");
  await page.goto("/");
  await page.getByRole("button", { name: /Practise the v sound/ }).click();
  await say(page, "Say it");
  await say(page, "Try again");
  await say(page, "Try again");
  await expect(page.getByText("Good effort. We'll come back to this one.")).toBeVisible();
  await expect(page.getByText("Nice work.")).toBeVisible({ timeout: 5000 });
  await expect(page.getByText("Shadow it: Once more")).toBeVisible();
});

test("Skip queues a drill; the summary says it comes back", async ({ page }) => {
  await stub(page, [SAY_BETTER, SHADOW]);
  await page.goto("/");
  await page.getByRole("button", { name: /Practise the v sound/ }).click();
  await expect(page.getByText("You said: I go market")).toBeVisible();
  await page.getByRole("button", { name: "Skip" }).click();
  await page.getByRole("button", { name: "Skip" }).click();
  await expect(page.getByText("Say it better: Skipped. It comes back next time.")).toBeVisible();
  const queue = await page.evaluate(() => JSON.parse(localStorage.getItem("bolo.drillQueue") ?? "[]").map((q: { drill: { key: string } }) => q.drill.key));
  expect(queue).toEqual(["sayBetter:i went", "shadow:it was"]);
});

test("Esc closes the drills, and the rest come back next time", async ({ page }) => {
  await stub(page, [SAY_BETTER, SHADOW]);
  await page.goto("/");
  await page.getByRole("button", { name: /Practise the v sound/ }).click();
  await expect(page.getByText("You said: I go market")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("heading", { level: 1, name: "Ready when you are." })).toBeVisible();
  const queue = await page.evaluate(() => JSON.parse(localStorage.getItem("bolo.drillQueue") ?? "[]").length);
  expect(queue).toBe(2);
});

test("say it better hides the key words in round 2", async ({ page }) => {
  await stub(page, [SAY_BETTER]);
  await page.goto("/");
  await page.getByRole("button", { name: /Practise the v sound/ }).click();
  await expect(page.getByText("I went to the market.")).toBeVisible();
  await say(page, "Say it");
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByText("I ____ to the market.")).toBeVisible();
});

test("on a phone, the drill fits and the pair stays side by side", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page, [PAIRS]);
  await page.goto("/");
  await page.getByRole("button", { name: /Practise the v sound/ }).click();
  await expect(page.getByRole("heading", { name: "Which did you hear?" })).toBeVisible();
  const a = (await page.locator(".bo-pair").nth(0).boundingBox())!;
  const b = (await page.locator(".bo-pair").nth(1).boundingBox())!;
  expect(Math.abs(a.y - b.y)).toBeLessThan(2);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});
