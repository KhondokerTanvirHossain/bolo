import { expect, test, type Page } from "./fixtures";

/** P3c (SET-5, SET-6, 2026-11-13): Me → Speaking → Bolo's voice and mood. The API is stubbed. */
const ACCOUNT = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
  profile: { goal: "work_meetings", fearedMoment: "speaking_up", talkMinutes: 5 },
  account: { email: "r@x.com", name: "Rafi" } };

/** A 2-second silent 24 kHz WAV, so "Playing" lasts long enough to see. */
function silentWav(seconds = 2): Buffer {
  const rate = 24000, n = rate * seconds * 2;
  const b = Buffer.alloc(44 + n);
  b.write("RIFF", 0); b.writeUInt32LE(36 + n, 4); b.write("WAVE", 8); b.write("fmt ", 12); b.writeUInt32LE(16, 16);
  b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(rate, 24); b.writeUInt32LE(rate * 2, 28); b.writeUInt16LE(2, 32);
  b.writeUInt16LE(16, 34); b.write("data", 36); b.writeUInt32LE(n, 40);
  return b;
}

type Saved = Record<string, unknown>[];

async function stub(page: Page, settings: object = { buddySpeed: "normal", captions: true, talkView: "chat", voiceGender: "female", voiceAccent: "british", mood: "friendly" }) {
  const saved: Saved = [];
  const samples: string[] = [];
  let current = { ...ACCOUNT, settings };
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await page.route("**/api/me", (r) => r.fulfill({ json: current }));
  await page.route("**/api/me/settings", (r) => {
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
  await page.route("**/api/voices/*/sample", (r) => {
    samples.push(r.request().url().split("/api/voices/")[1].split("/")[0]);
    return r.fulfill({ status: 200, contentType: "audio/wav", body: silentWav() });
  });
  return { saved, samples };
}

async function openMe(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
}

test.describe("phone", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test("Settings rows show the current voice and mood", async ({ page }) => {
    await stub(page, { buddySpeed: "normal", captions: true, voiceGender: "male", voiceAccent: "indian", mood: "calm" });
    await openMe(page);
    await expect(page.getByRole("button", { name: /Bolo's voice.*Indian · Male/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /Bolo's mood.*Calm/ })).toBeVisible();
  });

  test("choosing Male then American saves voiceGender male and voiceAccent american", async ({ page }) => {
    const { saved } = await stub(page);
    await openMe(page);
    await page.getByRole("button", { name: /Bolo's voice/ }).click();
    await expect(page.getByRole("heading", { name: "Bolo's voice" })).toBeVisible();
    await expect(page.getByText("Pick who Bolo sounds like. You'll hear it from your next talk.")).toBeVisible();
    await page.getByRole("radio", { name: "Male", exact: true }).click();
    await page.getByRole("radio", { name: /American/ }).click();
    await expect(page.getByRole("radio", { name: /American/ })).toHaveAttribute("aria-checked", "true");
    expect(saved.at(-1)).toMatchObject({ voiceGender: "male", voiceAccent: "american", buddySpeed: "normal", captions: true });
    await page.getByRole("button", { name: "Settings" }).click();
    await expect(page.getByRole("button", { name: /Bolo's voice.*American · Male/ })).toBeVisible();
  });

  test("Hear it plays the sample for that voice and shows Playing", async ({ page }) => {
    const { samples, saved } = await stub(page);
    await openMe(page);
    await page.getByRole("button", { name: /Bolo's voice/ }).click();
    await page.getByRole("button", { name: "Hear the American female voice" }).click();
    await expect(page.getByRole("button", { name: "Hear the American female voice" })).toHaveAttribute("aria-pressed", "true");
    await expect.poll(() => samples).toContain("american-female");
    expect(saved).toHaveLength(0);
  });

  test("one sample at a time", async ({ page }) => {
    await stub(page);
    await openMe(page);
    await page.getByRole("button", { name: /Bolo's voice/ }).click();
    await page.getByRole("button", { name: "Hear the British female voice" }).click();
    await expect(page.getByRole("button", { name: "Hear the British female voice" })).toHaveAttribute("aria-pressed", "true");
    await page.getByRole("button", { name: "Hear the Indian female voice" }).click();
    await expect(page.getByRole("button", { name: "Hear the Indian female voice" })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByRole("button", { name: "Hear the British female voice" })).toHaveAttribute("aria-pressed", "false");
  });

  test("choosing Funny saves mood funny", async ({ page }) => {
    const { saved } = await stub(page);
    await openMe(page);
    await page.getByRole("button", { name: /Bolo's mood/ }).click();
    await expect(page.getByText("How Bolo talks with you. It works with every voice, from your next talk.")).toBeVisible();
    await expect(page.getByText("“Good to see you, Rafi. How did the client demo go?”")).toBeVisible();
    await page.getByRole("radio", { name: /Funny/ }).click();
    await expect(page.getByRole("radio", { name: /Funny/ })).toHaveAttribute("aria-checked", "true");
    expect(saved.at(-1)).toMatchObject({ mood: "funny", voiceGender: "female", voiceAccent: "british" });
  });

  test("fits at 390 px", async ({ page }) => {
    await stub(page);
    await openMe(page);
    await page.getByRole("button", { name: /Bolo's voice/ }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
    await page.getByRole("button", { name: "Settings" }).click();
    await page.getByRole("button", { name: /Bolo's mood/ }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });
});

test.describe("desktop", () => {
  test.use({ viewport: { width: 1440, height: 1000 } });

  test("both pickers are on the Me page", async ({ page }) => {
    const { saved } = await stub(page);
    await openMe(page);
    await expect(page.getByRole("radiogroup", { name: "Accent" })).toBeVisible();
    await expect(page.getByRole("radiogroup", { name: "Bolo's mood" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Bolo's voice.*British · Female/ })).toHaveCount(0);
    await page.getByRole("radio", { name: /Professional/ }).click();
    expect(saved.at(-1)).toMatchObject({ mood: "professional" });
  });
});
