import { expect, test, type Page, type WebSocketRoute } from "./fixtures";

/** Bolo's hats and 10-minute talks (2026-11-16, DS v45). The API and the talk relay are stubbed. */
test.beforeEach(async ({ page, browserName }) => {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  if (browserName !== "firefox") return;
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    md.getUserMedia = () => original({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
  });
});

async function stub(page: Page, plan: string, settings: Record<string, unknown> = {}) {
  const saved: Record<string, unknown>[] = [];
  const starts: Record<string, unknown>[] = [];
  let current = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "work_meetings", fearedMoment: "speaking_up", talkMinutes: 5 }, account: { email: "r@x.com", name: "Rafi" },
    settings: { buddySpeed: "normal", captions: true, scenario: "free", difficulty: "realistic", ...settings } };
  await page.route("**/api/me", (r) => r.fulfill({ json: current }));
  await page.route("**/api/me/settings", (r) => {
    const body = JSON.parse(r.request().postData() ?? "{}");
    saved.push(body);
    current = { ...current, settings: body };
    return r.fulfill({ status: 204 });
  });
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan, window: "month", left: 2, total: plan === "free" ? 2 : 50, resetsOn: "2026-12-01" } } }));
  await page.route("**/api/talk", (r) => {
    const body = JSON.parse(r.request().postData() ?? "{}");
    starts.push(body);
    return r.fulfill({ status: 201, json: { id: "11111111-1111-1111-1111-111111111111", minutes: body.minutes ?? 5, scenario: body.scenario ?? "free" } });
  });
  return { saved, starts };
}

test("the picker offers four hats; English teacher applies and names the Talk line", async ({ page }) => {
  const { saved } = await stub(page, "free");
  await page.goto("/");
  await page.getByRole("button", { name: "Change" }).click();
  const dialog = page.getByRole("dialog", { name: "What do you want to practise?" });
  for (const name of [/Free talk/, /English teacher/, /Standup rehearsal/, /IELTS Speaking practice/]) {
    await expect(dialog.getByRole("radio", { name })).toBeVisible();
  }
  await expect(dialog.getByText("An examiner asks Parts 1, 2 and 3. Practice only, no band score.")).toBeVisible();
  await dialog.getByRole("radio", { name: /English teacher/ }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText(/English teacher · 5 min/)).toBeVisible();
  await expect.poll(() => saved.at(-1)).toMatchObject({ scenario: "teacher" });
});

test("free plan: 10 min is off and points to Premium", async ({ page }) => {
  await stub(page, "free");
  await page.goto("/");
  await page.getByRole("button", { name: "Change" }).click();
  await expect(page.getByRole("button", { name: "10 min" })).toBeDisabled();
  await expect(page.getByText("10-minute talks come with Premium.")).toBeVisible();
});

test("Premium chooses 10 minutes; the line and the start carry it", async ({ page }) => {
  const { saved, starts } = await stub(page, "premium", { scenario: "ielts" });
  await page.routeWebSocket("**/ws/talk/**", (ws) => { ws.onMessage(() => {}); });
  await page.goto("/");
  await expect(page.getByText(/IELTS practice · 5 min/)).toBeVisible();
  await page.getByRole("button", { name: "Change" }).click();
  await expect(page.getByText("10-minute talks come with Premium.")).toHaveCount(0);
  await page.getByRole("button", { name: "10 min" }).click();
  await expect(page.getByRole("button", { name: "10 min" })).toHaveAttribute("aria-pressed", "true");
  await expect.poll(() => saved.at(-1)).toMatchObject({ talkMinutes: 10, scenario: "ielts" });
  await page.keyboard.press("Escape");
  await expect(page.getByText(/IELTS practice · 10 min/)).toBeVisible();
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => starts.length).toBe(1);
  expect(starts[0]).toMatchObject({ scenario: "ielts", minutes: 10 });
});

test("in an English teacher talk, the Say it better card shows and then the judgement", async ({ page }) => {
  await stub(page, "free", { scenario: "teacher" });
  let route: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (ws) => { route = ws; ws.onMessage(() => {}); });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => route !== null).toBe(true);
  const send = (m: object) => route!.send(JSON.stringify(m));
  send({ type: "state", value: "buddy_speaking", leftMs: 222000 });
  send({ type: "caption", turn: 0, text: "What would you like to practise saying today?" });
  send({ type: "buddy_done", turn: 0 });
  send({ type: "state", value: "your_turn", leftMs: 222000 });
  await expect(page.getByText("English teacher").first()).toBeVisible();
  send({ type: "teach", better: "I need to tell the client the release is delayed.", changed: ["need", "delayed"], why: "Smoother.", kind: "grammar" });
  const card = page.getByRole("note", { name: "Say it better" });
  await expect(card).toContainText("I need to tell the client the release is delayed.");
  await expect(card).toContainText("Say it better · Grammar");
  send({ type: "judged", result: "nailed" });
  await expect(card.getByRole("status")).toHaveText(/Nailed it/);
  send({ type: "state", value: "buddy_speaking", leftMs: 200000 });
  send({ type: "state", value: "your_turn", leftMs: 190000 });
  await expect(card).toHaveCount(0);
});

/** Founder 2026-11-18 (DS v46 ScreenBanglaTurn): a turn said in Bangla shows in Bangla script; the teacher gives the English. */
test("a Bangla turn shows in Bangla, and the teacher's card says In English", async ({ page }) => {
  await stub(page, "free", { scenario: "teacher" });
  let route: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (ws) => { route = ws; ws.onMessage(() => {}); });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => route !== null).toBe(true);
  const send = (m: object) => route!.send(JSON.stringify(m));
  send({ type: "state", value: "buddy_speaking", leftMs: 222000 });
  send({ type: "caption", turn: 0, text: "Is there anything you'd like to learn today?" });
  send({ type: "buddy_done", turn: 0 });
  send({ type: "state", value: "your_turn", leftMs: 222000 });
  send({ type: "turn_end", turn: 1, text: "আমার বস শেষ মুহূর্তে পুরো পরিকল্পনা বদলে দিয়েছে।" });
  const said = page.getByText("আমার বস শেষ মুহূর্তে পুরো পরিকল্পনা বদলে দিয়েছে।");
  await expect(said).toBeVisible();
  await expect(said).toHaveAttribute("lang", "bn");
  send({ type: "teach", better: "My boss changed the whole plan at the last minute.", changed: [], why: "Here it is in English.", kind: "english" });
  await expect(page.getByRole("note", { name: "Say it better" })).toContainText("Say it better · In English");
});
