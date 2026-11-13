import { expect, test, type Page } from "./fixtures";

/** AX-7 (changed 2026-11-14) and AX-8: Me → Speaking → Time to answer and I stammer. The API is stubbed. */
const ACCOUNT = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
  profile: { goal: "work_meetings", fearedMoment: "speaking_up", talkMinutes: 5 },
  account: { email: "r@x.com", name: "Rafi" } };
const SETTINGS = { buddySpeed: "normal", captions: true, talkView: "chat", voiceGender: "female", voiceAccent: "british", mood: "friendly",
  answerTime: "auto", answerTimeAuto: "relaxed", stammer: false };

async function stub(page: Page, fail = false) {
  const saved: Record<string, unknown>[] = [];
  let current = { ...ACCOUNT, settings: SETTINGS };
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await page.route("**/api/me", (r) => r.fulfill({ json: current }));
  await page.route("**/api/me/settings", (r) => {
    if (fail) return r.fulfill({ status: 500, json: { code: "unknown" } });
    const body = JSON.parse(r.request().postData() ?? "{}");
    saved.push(body);
    current = { ...current, settings: body };
    return r.fulfill({ status: 204 });
  });
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/memory", (r) => r.fulfill({ json: { enabled: true, followUps: [], about: [], interests: [] } }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan: "free", window: "month", left: 2, total: 2, resetsOn: "2026-12-01" } } }));
  return saved;
}

async function openMe(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
}

test.describe("phone", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test("the row shows Automatic and Bolo's pick", async ({ page }) => {
    await stub(page);
    await openMe(page);
    await expect(page.getByRole("button", { name: /Time to answer.*Automatic · Relaxed/ })).toBeVisible();
  });

  test("choosing Quick saves answerTime quick", async ({ page }) => {
    const saved = await stub(page);
    await openMe(page);
    await page.getByRole("button", { name: /Time to answer/ }).click();
    await expect(page.getByRole("heading", { name: "Time to answer" })).toBeVisible();
    await expect(page.getByText("How long Bolo waits for you to start, and how soon it replies when you stop. From your next talk.")).toBeVisible();
    await expect(page.getByText("Bolo learns how long you take to start answering and matches it. Right now: Relaxed")).toBeVisible();
    await expect(page.getByText("Bolo always helps if you stay quiet, and pauses the talk if you stay quiet for long.")).toBeVisible();
    await page.getByRole("radio", { name: /Quick/ }).click();
    await expect(page.getByRole("radio", { name: /Quick/ })).toHaveAttribute("aria-checked", "true");
    expect(saved.at(-1)).toMatchObject({ answerTime: "quick", mood: "friendly", stammer: false });
    await page.getByRole("button", { name: "Settings" }).click();
    await expect(page.getByRole("button", { name: /Time to answer.*Quick/ })).toBeVisible();
  });

  test("a failed save puts Automatic back", async ({ page }) => {
    await stub(page, true);
    await openMe(page);
    await page.getByRole("button", { name: /Time to answer/ }).click();
    await page.getByRole("radio", { name: /^Relaxed/ }).click();
    await expect(page.getByText("That didn't save. Try again.")).toBeVisible();
    await expect(page.getByRole("radio", { name: /Automatic/ })).toHaveAttribute("aria-checked", "true");
  });

  test("the I stammer switch saves stammer true", async ({ page }) => {
    const saved = await stub(page);
    await openMe(page);
    const sw = page.getByRole("switch", { name: "I stammer" });
    await expect(sw).toHaveAttribute("aria-checked", "false");
    await expect(page.getByText("Bolo waits through repeats and pauses, and your feedback won't count them")).toBeVisible();
    await sw.click();
    await expect(sw).toHaveAttribute("aria-checked", "true");
    expect(saved.at(-1)).toMatchObject({ stammer: true, answerTime: "auto" });
  });

  test("fits at 390 px", async ({ page }) => {
    await stub(page);
    await openMe(page);
    await page.getByRole("button", { name: /Time to answer/ }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });
});

test.describe("desktop", () => {
  test.use({ viewport: { width: 1440, height: 1000 } });

  test("Time to answer is chosen in place on the Me page", async ({ page }) => {
    const saved = await stub(page);
    await openMe(page);
    const group = page.getByRole("radiogroup", { name: "Time to answer" });
    await expect(group).toBeVisible();
    await expect(page.getByText("Automatic matches how long you take to start. Right now: Relaxed")).toBeVisible();
    await expect(page.getByRole("button", { name: /Time to answer.*Automatic/ })).toHaveCount(0);
    await group.getByRole("radio", { name: "Relaxed" }).click();
    expect(saved.at(-1)).toMatchObject({ answerTime: "relaxed" });
    await expect(page.getByRole("switch", { name: "I stammer" })).toBeVisible();
  });
});
