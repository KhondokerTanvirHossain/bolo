import { expect, test, type Page } from "./fixtures";

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
type ProfileBody = { goal: string; fearedMoment: string; talkMinutes: number };

/** Stubs the Java API so the browser flow is pinned on its own; LearnerAcceptanceTest pins the real API.
 *  Like the real API, GET /api/me is 401 until POST /api/session has created the guest. */
async function stubApi(page: Page, opts: { consent?: boolean; profile?: boolean; consentFailures?: number[]; profileFailures?: number[] } = {}) {
  const sent: ConsentBody[] = [];
  const profiles: ProfileBody[] = [];
  const failures = [...(opts.consentFailures ?? [])];
  const profileFailures = [...(opts.profileFailures ?? [])];
  let sessions = 0;
  let hasSession = Boolean(opts.consent || opts.profile);
  let consent = opts.consent ? { keepRecordings: true, policyVersion: "2026-11-v1" } : null;
  let profile = opts.profile ? { goal: "work_meetings", fearedMoment: "small_talk", talkMinutes: 5 } : null;
  const learner = () => ({ learnerId: "00000000-0000-0000-0000-000000000001", kind: "guest", consent, profile });
  await page.route("**/api/me", (r) =>
    hasSession ? r.fulfill({ json: learner() }) : r.fulfill({ status: 401, json: { code: "signed-out" } }));
  await page.route("**/api/session", (r) => { sessions++; hasSession = true; return r.fulfill({ json: learner() }); });
  // Funnel events are fire-and-forget; answer them so they never compete with the saves this test checks.
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/profile", async (r) => {
    expect(r.request().method()).toBe("PUT");
    const pf = profileFailures.shift();
    if (pf) { await r.fulfill({ status: pf, json: { code: pf === 401 ? "signed-out" : "oops" } }); return; }
    const body = r.request().postDataJSON() as ProfileBody;
    profiles.push(body);
    profile = body;
    await r.fulfill({ status: 204 });
  });
  await page.route("**/api/consent", async (r) => {
    expect(r.request().headers()["x-bolo"]).toBe("1");
    const failure = failures.shift();
    if (failure) {
      const code = failure === 401 ? "signed-out" : failure === 429 ? "slow-down" : "oops";
      await r.fulfill({ status: failure, json: { code } });
      return;
    }
    if (!hasSession) { await r.fulfill({ status: 401, json: { code: "signed-out" } }); return; }
    const body = r.request().postDataJSON() as ConsentBody;
    sent.push(body);
    consent = { keepRecordings: body.keepRecordings, policyVersion: body.policyVersion };
    await r.fulfill({ status: 204 });
  });
  return { consents: sent, profiles, sessions: () => sessions };
}

/**
 * Seeds the stored onboarding state before any of the page's code runs. Writing it after load raced the app's own
 * save on boot, a suspected cause of CI flakes on reload tests.
 */
async function seed(page: Page, json: string) {
  await page.addInitScript((value) => {
    if (!sessionStorage.getItem("bolo.e2e.seeded")) {
      localStorage.setItem("bolo.onboarding.v1", value);
      sessionStorage.setItem("bolo.e2e.seeded", "1");
    }
  }, json);
}

/** True when an AudioContext's clock advances in this browser environment. Bounded: with no audio output,
 *  resume() and close() can hang forever, so nothing here is awaited without a deadline. */
async function audioRenders(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const deadline = <T,>(p: Promise<T>, ms: number, fallback: T) =>
      Promise.race([p, new Promise<T>((r) => setTimeout(() => r(fallback), ms))]);
    const ctx = new AudioContext();
    return deadline(ctx.resume().then(() => true, () => true), 1000, false).then(async () => {
      const start = ctx.currentTime;
      await new Promise((r) => setTimeout(r, 600));
      const advanced = ctx.currentTime > start;
      void deadline(ctx.close(), 500, undefined);
      return advanced;
    });
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
  await expect(page.getByRole("heading", { name: "Choose your microphone" })).toBeVisible();
  expect(sent.consents).toEqual([{ keepRecordings: true, over13: true, policyVersion: "2026-11-v1" }]);
  // The guest is created at consent, not on page load.
  expect(sent.sessions()).toBe(1);

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

  // Setup questions (US-1.4): keys pick chips, Back keeps answers.
  await expect(page.getByRole("heading", { name: "What do you need English for most?" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Next" })).toBeDisabled();
  await page.keyboard.press("2");
  await expect(page.getByRole("button", { name: "Job interviews" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByRole("heading", { name: "Which moment feels hardest?" })).toBeVisible();
  await page.getByRole("button", { name: "Phone calls" }).click();
  await page.getByRole("button", { name: "Back" }).click();
  await expect(page.getByRole("button", { name: "Job interviews" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByRole("button", { name: "Phone calls" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("button", { name: "10 minutes" }).click();
  await page.getByRole("button", { name: "Next" }).click();
  await expect.poll(() => sent.profiles).toEqual([{ goal: "job_interviews", fearedMoment: "phone_calls", talkMinutes: 10 }]);
  await expect(page.getByRole("heading", { name: "Introduce yourself, like at a new job" })).toBeVisible();

  // Day 1 clip (US-1.6): a short clip gets one "say a bit more?" prompt; Keep it saves it on this device.
  await page.getByRole("button", { name: "Start recording" }).click();
  await expect(page.getByRole("button", { name: "Stop recording" })).toBeVisible();
  await page.waitForTimeout(2500);
  await page.keyboard.press("Space");
  await expect(page.getByRole("heading", { name: "Want to say a bit more?" })).toBeVisible();
  await page.getByRole("button", { name: "Keep it" }).click();
  await expect(page.getByText("Saved. We'll play this back to you on Day 7.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Play my Day 1 clip" })).toBeVisible({ timeout: 5000 });
  const audio = page.getByTestId("day1-audio");
  await expect.poll(() => audio.evaluate((a: HTMLAudioElement) => a.readyState)).toBeGreaterThanOrEqual(1);
  if (await audioRenders(page)) {
    await page.getByRole("button", { name: "Play my Day 1 clip" }).click();
    await expect.poll(() => audio.evaluate((a: HTMLAudioElement) => a.currentTime)).toBeGreaterThan(0);
  }
});

test("a reload on the Day 1 step comes back to an empty recorder", async ({ page }) => {
  await stubApi(page, { consent: true, profile: true });
  await seed(page, JSON.stringify({ step: "day1", over13: true,
    keepRecordings: true, micDeviceId: null, headphones: false, questionIndex: 2, answers: {} }));
  await page.goto("/");
  await page.getByRole("button", { name: "Start recording" }).click();
  await expect(page.getByRole("button", { name: "Stop recording" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button", { name: "Start recording" })).toBeVisible();
  await expect(page.getByText("0:30")).toHaveCount(0);
});

test("a reload on the microphone step resumes there", async ({ page }) => {
  await stubApi(page, { consent: true });
  await seed(page,
    JSON.stringify({ step: "mic", over13: true, keepRecordings: true, micDeviceId: null, headphones: false }));
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Choose your microphone" })).toBeVisible();
});

test("without consent on the server, a stored later step returns to consent", async ({ page }) => {
  await stubApi(page);
  await seed(page,
    JSON.stringify({ step: "mic", over13: true, keepRecordings: true, micDeviceId: null, headphones: false }));
  await page.goto("/");
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

test("page views alone create no guest", async ({ page }) => {
  const api = await stubApi(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Meet Bolo" }).click();
  await page.getByRole("button", { name: "Skip intro" }).click();
  await expect(page.getByRole("heading", { name: "Bolo listens only while you practise." })).toBeVisible();
  expect(api.sessions()).toBe(0);
});

test("a consent that fails to save says so, and choosing again works", async ({ page }) => {
  await stubApi(page, { consent: false, consentFailures: [500] });
  await page.goto("/");
  await page.getByRole("button", { name: "Meet Bolo" }).click();
  await page.getByRole("button", { name: "Skip intro" }).click();
  await page.getByText("I'm 13 or older").click();
  await page.getByRole("button", { name: "Yes, keep them" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "That didn't save" })).toBeVisible();
  await page.getByRole("button", { name: "Yes, keep them" }).click();
  await expect(page.getByRole("heading", { name: "Choose your microphone" })).toBeVisible();
});

test("an expired session at consent starts a new one and carries on", async ({ page }) => {
  const api = await stubApi(page, { consentFailures: [401] });
  await page.goto("/");
  await page.getByRole("button", { name: "Meet Bolo" }).click();
  await page.getByRole("button", { name: "Skip intro" }).click();
  await page.getByText("I'm 13 or older").click();
  await page.getByRole("button", { name: "No, just feedback" }).click();
  await expect(page.getByRole("heading", { name: "Choose your microphone" })).toBeVisible();
  expect(api.consents).toHaveLength(1);
});

test("a remembered microphone that is gone falls back to the default one", async ({ page }) => {
  await stubApi(page, { consent: true });
  await seed(page, JSON.stringify({ step: "mic", over13: true,
    keepRecordings: true, micDeviceId: "unplugged-usb-headset", headphones: false, questionIndex: 0, answers: {} }));
  await page.goto("/");
  await expect(page.locator("[data-mic-status]")).toHaveAttribute("data-mic-status", "live");
  await expect(page.getByRole("heading", { name: "Choose your microphone" })).toBeVisible();
});

/** Lands on the Day 1 card with consent and answers already on the server. */
async function atDay1(page: Page) {
  await stubApi(page, { consent: true, profile: true });
  await seed(page, JSON.stringify({ step: "day1", over13: true,
    keepRecordings: true, micDeviceId: null, headphones: false, micCheck: "great", questionIndex: 2, answers: {} }));
  await page.goto("/");
}

test("two starts in one tick open one mic, and Stop releases it", async ({ page }) => {
  // Count every stream the page opens, so a leaked second mic shows up as a live track after Stop.
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const open = md.getUserMedia.bind(md);
    const w = window as unknown as { __streams: MediaStream[] };
    w.__streams = [];
    md.getUserMedia = async (c?: MediaStreamConstraints) => { const s = await open(c); w.__streams.push(s); return s; };
  });
  await atDay1(page);
  await expect(page.getByRole("button", { name: "Start recording" })).toBeVisible();
  await page.evaluate(() => {
    const start = document.querySelector<HTMLButtonElement>('[aria-label="Start recording"]')!;
    start.click();
    start.click();
  });
  await expect(page.getByRole("button", { name: "Stop recording" })).toBeVisible();
  await page.waitForTimeout(1500);
  await page.getByRole("button", { name: "Stop recording" }).click();
  await expect(page.getByRole("heading", { name: "Want to say a bit more?" })).toBeVisible();
  const live = await page.evaluate(() => (window as unknown as { __streams: MediaStream[] }).__streams
    .flatMap((s) => s.getTracks()).filter((t) => t.readyState === "live").length);
  expect(live).toBe(0);
});

test("leaving the tab mid-recording throws the clip away (US-1.7)", async ({ page }) => {
  await atDay1(page);
  await page.getByRole("button", { name: "Start recording" }).click();
  await expect(page.getByRole("button", { name: "Stop recording" })).toBeVisible();
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { configurable: true, get: () => "hidden" });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(page.getByRole("button", { name: "Start recording" })).toBeVisible();
});

test("a clip of 10 seconds or more can still be recorded again (US-1.6)", async ({ page }) => {
  test.setTimeout(45_000);
  await atDay1(page);
  await page.getByRole("button", { name: "Start recording" }).click();
  await page.waitForTimeout(10_500);
  await page.getByRole("button", { name: "Stop recording" }).click();
  await expect(page.getByText("Saved. We'll play this back to you on Day 7.")).toBeVisible();
  await page.getByRole("button", { name: "Record again" }).click();
  await expect(page.getByRole("button", { name: "Stop recording" })).toBeVisible();
});

test("a session lost during the questions goes back to consent, never past it", async ({ page }) => {
  await stubApi(page, { consent: true, profileFailures: [401] });
  await seed(page, JSON.stringify({ step: "setup", over13: true,
    keepRecordings: true, micDeviceId: null, headphones: false, micCheck: "great", questionIndex: 2,
    answers: { goal: "work_meetings", fearedMoment: "small_talk" } }));
  await page.goto("/");
  await page.getByRole("button", { name: "5 minutes" }).click();
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByRole("heading", { name: "Bolo listens only while you practise." })).toBeVisible();
});

test("a busy server at consent says so in plain words", async ({ page }) => {
  await stubApi(page, { consentFailures: [429] });
  await page.goto("/");
  await page.getByRole("button", { name: "Meet Bolo" }).click();
  await page.getByRole("button", { name: "Skip intro" }).click();
  await page.getByText("I'm 13 or older").click();
  await page.getByRole("button", { name: "Yes, keep them" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Lots of people are joining" })).toBeVisible();
});

test("no connection says so on a card in the middle of the screen", async ({ page }) => {
  await page.route("**/api/**", (r) => r.abort("internetdisconnected"));
  await page.goto("/");
  const card = page.getByRole("alert").filter({ hasText: "Bolo can't reach the internet" });
  await expect(card).toBeVisible();
  const box = (await card.boundingBox())!;
  const vp = page.viewportSize()!;
  expect(box.x + box.width).toBeLessThanOrEqual(vp.width);
  expect(box.y + box.height).toBeLessThanOrEqual(vp.height);
  expect(Math.abs(box.x + box.width / 2 - vp.width / 2)).toBeLessThan(4);
});
