import { describe, expect, it } from "vitest";
import { afterDebrief, initialLoop, reminderAnswered, saveAnswered, seenSeeYou, signedInAfterFirstLoop } from "./loop";

describe("the end of the first loop (US-6.8) and the guest save prompts (US-8.2)", () => {
  it("asks a guest to save after the first talk, then says see you tomorrow", () => {
    const a = afterDebrief(initialLoop, { guest: true });
    expect(a.pendingSave).toBe(true);
    const b = saveAnswered(a);
    expect(b.pendingSave).toBe(false);
    expect(b.seeYou).toBe(true);
    expect(seenSeeYou(b).seeYou).toBe(false);
  });

  it("asks again once after the 2nd and 3rd talks, never after the 4th or a repeat", () => {
    let l = saveAnswered(afterDebrief(initialLoop, { guest: true }));
    l = afterDebrief(l, { guest: true });
    expect(l.pendingSave).toBe(true);
    l = saveAnswered(l);
    l = afterDebrief(l, { guest: true });
    expect(l.pendingSave).toBe(true);
    l = saveAnswered(l);
    l = afterDebrief(l, { guest: true });
    expect(l.pendingSave).toBe(false);
  });

  it("never asks an account, but still says see you tomorrow after the first loop", () => {
    const a = afterDebrief(initialLoop, { guest: false });
    expect(a.pendingSave).toBe(false);
    expect(a.seeYou).toBe(true);
  });

  it("a missed save step stays pending until it is answered", () => {
    const a = afterDebrief(initialLoop, { guest: true });
    expect(afterDebrief(a, { guest: true }).pendingSave).toBe(true);
  });

  it("after the first talk asks for a reminder time before 'see you tomorrow' (PRG-7)", () => {
    const a = afterDebrief(initialLoop, { guest: false });
    expect(a.askReminder).toBe(true);
    const b = reminderAnswered(a);
    expect(b.askReminder).toBe(false);
    expect(b.seeYou).toBe(true);
    expect(afterDebrief(b, { guest: false }).askReminder).toBe(false);
  });

  it("a guest is asked for a reminder only once signed in, never as a guest", () => {
    const a = afterDebrief(initialLoop, { guest: true });
    expect(a.askReminder).toBe(false);
    expect(signedInAfterFirstLoop(saveAnswered(a)).askReminder).toBe(true);
    expect(signedInAfterFirstLoop(afterDebrief(saveAnswered(afterDebrief(initialLoop, { guest: true })), { guest: true })).askReminder).toBe(false);
  });
});
