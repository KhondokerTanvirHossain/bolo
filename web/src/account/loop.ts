/**
 * The end of the first loop (US-6.8, PRG-7) and the guest save prompts (US-8.2), as pure state kept in this browser.
 * A finished talk is one whose ready feedback was seen. Guests are asked to save after talks 1, 2 and 3 (once each);
 * an unanswered ask stays pending and is shown before Home next time. "See you tomorrow" follows the first loop once.
 */
export type Loop = { talksFinished: number; asked: number[]; pendingSave: boolean; seeYou: boolean };

export const initialLoop: Loop = { talksFinished: 0, asked: [], pendingSave: false, seeYou: false };

const ASK_AFTER = [1, 2, 3];

export function afterDebrief(loop: Loop, { guest }: { guest: boolean }): Loop {
  const talks = loop.talksFinished + 1;
  const first = talks === 1;
  if (!guest) return { ...loop, talksFinished: talks, seeYou: loop.seeYou || first };
  const ask = ASK_AFTER.includes(talks) && !loop.asked.includes(talks);
  return { ...loop, talksFinished: talks, asked: ask ? [...loop.asked, talks] : loop.asked, pendingSave: loop.pendingSave || ask };
}

/** Signed in, or "Stay a guest": the step is done; after the first loop, Home says see you tomorrow. */
export function saveAnswered(loop: Loop): Loop {
  return { ...loop, pendingSave: false, seeYou: loop.seeYou || loop.talksFinished === 1 };
}

export function seenSeeYou(loop: Loop): Loop {
  return { ...loop, seeYou: false };
}

const KEY = "bolo.loop.v1";

export function loadLoop(): Loop {
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? { ...initialLoop, ...(JSON.parse(raw) as Partial<Loop>) } : initialLoop;
  } catch {
    return initialLoop;
  }
}

export function saveLoop(loop: Loop): void {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(loop));
  } catch {
    // per-browser state only
  }
}
