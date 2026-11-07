import { expect, test, type Page } from "@playwright/test";

// Firefox's fake stream goes silent when echo cancellation or noise suppression is requested (a fake-device
// artefact, not app behaviour), so on Firefox only the test strips those constraints. The app keeps them.
test.beforeEach(async ({ page, browserName }) => {
  if (browserName !== "firefox") return;
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    md.getUserMedia = (c?: MediaStreamConstraints) => {
      const audio = c?.audio;
      const deviceId = typeof audio === "object" ? audio.deviceId : undefined;
      const raw = { echoCancellation: false, noiseSuppression: false, autoGainControl: false };
      return original({ audio: deviceId ? { ...raw, deviceId } : raw });
    };
  });
});

type ConsentBody = { keepRecordings: boolean; over13: boolean; policyVersion: string };

/** Stubs the Java API so the browser flow is pinned on its own; LearnerAcceptanceTest pins the real API. */
async function stubApi(page: Page, opts: { consent?: boolean } = {}) {
  const sent: ConsentBody[] = [];
  let consent = opts.consent ? { keepRecordings: true, policyVersion: "2026-11-v1" } : null;
  await page.route("**/api/session", (r) =>
    r.fulfill({ json: { learnerId: "00000000-0000-0000-0000-000000000001", kind: "guest", consent } }));
  await page.route("**/api/consent", async (r) => {
    expect(r.request().headers()["x-bolo"]).toBe("1");
    const body = r.request().postDataJSON() as ConsentBody;
    sent.push(body);
    consent = { keepRecordings: body.keepRecordings, policyVersion: body.policyVersion };
    await r.fulfill({ status: 204 });
  });
  return sent;
}

/** True when an AudioContext's clock advances in this browser environment. */
async function audioRenders(page: Page): Promise<boolean> {
  return page.evaluate(async () => {
    const ctx = new AudioContext();
    await ctx.resume().catch(() => {});
    const start = ctx.currentTime;
    await new Promise((r) => setTimeout(r, 600));
    const advanced = ctx.currentTime > start;
    await ctx.close();
    return advanced;
  });
}

test("Meet Bolo through consent to the microphone check", async ({ page }) => {
  const sent = await stubApi(page);
  await page.goto("/");
  await expect(page.getByText("Talk first. Get better after.")).toBeVisible();
  await expect(page).toHaveTitle("Bolo");

  await page.getByRole("button", { name: "Meet Bolo" }).click();
  await expect(page.getByText("Hi, I'm Bolo.")).toBeVisible();
  await page.getByRole("button", { name: "Skip intro" }).click();

  const keep = page.getByRole("button", { name: "Yes, keep them" });
  await expect(keep).toBeDisabled();
  await expect(page.getByRole("button", { name: "No, just feedback" })).toBeDisabled();
  await page.getByText("I'm 13 or older").click();
  await keep.click();
  expect(sent).toEqual([{ keepRecordings: true, over13: true, policyVersion: "2026-11-v1" }]);

  await expect(page.getByRole("heading", { name: "Choose your microphone" })).toBeVisible();
  await expect(page.locator("[data-mic-status]")).toHaveAttribute("data-mic-status", "live");
  await expect(page.getByRole("combobox", { name: "Microphone" })).not.toHaveValue("");
  // The fake device plays a tone, so the meter lights up — where Web Audio renders at all. Headless Linux
  // runners have no audio output, so the graph never advances there; record that instead of failing.
  if (await audioRenders(page)) {
    await expect(page.locator(".bo-level i.on").first()).toBeVisible({ timeout: 5000 });
  } else {
    test.info().annotations.push({ type: "skipped-check", description: "meter: Web Audio does not render here" });
  }

  await page.getByRole("checkbox", { name: "I'm using headphones" }).check();
  await page.getByRole("button", { name: /continue/i }).click();
  await expect(page.getByRole("heading", { name: "Next: three quick questions" })).toBeVisible();
});

test("a reload on the microphone step resumes there", async ({ page }) => {
  await stubApi(page, { consent: true });
  await page.goto("/");
  await page.evaluate(() => localStorage.setItem("bolo.onboarding.v1",
    JSON.stringify({ step: "mic", over13: true, keepRecordings: true, micDeviceId: null, headphones: false })));
  await page.reload();
  await expect(page.getByRole("heading", { name: "Choose your microphone" })).toBeVisible();
});

test("without consent on the server, a stored later step returns to consent", async ({ page }) => {
  await stubApi(page);
  await page.goto("/");
  await page.evaluate(() => localStorage.setItem("bolo.onboarding.v1",
    JSON.stringify({ step: "mic", over13: true, keepRecordings: true, micDeviceId: null, headphones: false })));
  await page.reload();
  await expect(page.getByRole("heading", { name: "Bolo listens only while you practise." })).toBeVisible();
});

test("under 13 stops onboarding kindly", async ({ page }) => {
  await stubApi(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Meet Bolo" }).click();
  await page.getByRole("button", { name: "Skip intro" }).click();
  await page.getByRole("button", { name: "I'm under 13" }).click();
  await expect(page.getByText("Bolo is for ages 13 and up")).toBeVisible();
});
