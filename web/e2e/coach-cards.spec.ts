import { expect, test, type Page } from "./fixtures";

/** Founder feedback 2026-11-18 (DS v46 GrammarCard, VocabularyCard): the Coach's two new cards. */
const T = "77777777-7777-7777-7777-777777777777";
const ROW = () => ({ talkId: T, startedAt: new Date().toISOString(), minutes: 5, topic: "A busy Monday", status: "ready",
  fixes: ["Past events: went, not go", "\"very big\" → huge", "Say it better"], scenario: "free" });

async function stub(page: Page, settings: Record<string, unknown> = {}) {
  const saved: Record<string, unknown>[] = [];
  let current = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "work_meetings", fearedMoment: "speaking_up", talkMinutes: 5 }, account: { email: "r@x.com", name: "Rafi" },
    settings: { buddySpeed: "normal", captions: true, scenario: "free", difficulty: "realistic", ...settings } };
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
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan: "free", window: "month", left: 2, total: 2, resetsOn: "2026-12-01" } } }));
  await page.route("**/api/progress", (r) => r.fulfill({ json: { effort: { talks: 1, minutes: 5 }, trends: { sessions: 1, after: 3, skills: {} },
    patterns: { active: [], fixed: [] }, latest: [ROW()] } }));
  await page.route("**/api/history", (r) => r.fulfill({ json: { sessions: [ROW()] } }));
  await page.route(`**/api/talk/${T}/debrief/viewed`, (r) => r.fulfill({ status: 204 }));
  await page.route(`**/api/talk/${T}/debrief`, (r) => r.fulfill({ json: {
    status: "ready", topic: "A busy Monday", scenario: "free",
    strength: { text: "You told the story in order.", clip: null },
    fixes: [
      { id: "f1", kind: "grammar", title: "Past events: went, not go", said: "Yesterday I go to the bank", turn: 1,
        better: "Yesterday I went to the bank", changed: ["went"], why: "Yesterday is finished, so the verb moves to the past.", clip: null, model: null },
      { id: "f2", kind: "vocabulary", title: "\"very big\" → huge", used: "very big", word: "huge", said: "The traffic was very big", turn: 3,
        better: "The traffic jam was huge today", changed: ["huge"], why: "One strong word sounds surer.", clip: null, model: null },
      { id: "f3", kind: "upgrade", title: "Say it better", said: "I am tired", turn: 5, better: "I'm really tired after work",
        changed: ["really"], clip: null, model: null }],
    skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: false } }));
  await page.route(`**/api/talk/${T}/chat`, (r) => r.fulfill({ json: { topic: "A busy Monday", turns: [], marks: [] } }));
  return { saved };
}


test("the feedback shows a Grammar card and a Vocabulary card", async ({ page }) => {
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await page.getByRole("button", { name: "See all" }).click();
  await page.getByRole("button", { name: /A busy Monday/ }).click();
  await page.getByText("Past events: went, not go").first().click();
  await expect(page.getByText(/Grammar · 1 of 3/)).toBeVisible();
  await expect(page.getByText("\"Yesterday I go to the bank\"")).toBeVisible();
  await expect(page.getByText("Yesterday is finished, so the verb moves to the past.")).toBeVisible();
  await expect(page.getByRole("button", { name: /Try it/ }).first()).toBeVisible();
  await page.getByText("\"very big\" → huge").first().click();
  await expect(page.getByText(/Vocabulary · 2 of 3/)).toBeVisible();
  await expect(page.getByText("\"The traffic was very big\"")).toBeVisible();
  await expect(page.getByText("One strong word sounds surer.")).toBeVisible();
});
