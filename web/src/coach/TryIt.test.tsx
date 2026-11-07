// @vitest-environment jsdom
import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Outcome } from "@/practice/attempts";

let report: ((o: Outcome) => void) | undefined;
const seen: { mode?: string }[] = [];
vi.mock("@/practice/ReadAloud", () => ({
  ReadAloud: (p: { onOutcome?: (o: Outcome) => void; mode?: string }) => { report = p.onOutcome; seen.push({ mode: p.mode }); return <button>Try it</button>; },
}));

import { TryIt } from "./TryIt";

const once = (level: Outcome["level"]): Outcome => ({ level, heard: true, focus: level === "NAILED" ? [] : [{ word: "very", kind: "SOUND", title: "The v sound", tip: "Top teeth on lip." }] });

describe("TryIt", () => {
  afterEach(cleanup);

  /** Review: the third try's result must still be shown, above the kind "takes practice" note. */
  it("shows the third result with the takes-practice note", () => {
    render(<TryIt reference="Very good" deviceId={null} kind="sound" />);
    act(() => { report!(once("ONCE_MORE")); report!(once("CLOSER")); report!(once("CLOSER")); });
    expect(screen.getByText(/takes practice/i)).toBeTruthy();
    expect(screen.getByText("Closer")).toBeTruthy();
    expect(screen.getByText(/very/)).toBeTruthy();
  });

  /** Review: the server refuses references over 30 words; long upgrades shouldn't offer a button that can't work. */
  it("is not offered for a long sentence", () => {
    const long = Array.from({ length: 21 }, (_, i) => `word${i}`).join(" ");
    const { container } = render(<TryIt reference={long} deviceId={null} kind="upgrade" />);
    expect(container.textContent).toBe("");
  });

  it("checks an upgrade for clarity, not word for word", () => {
    seen.length = 0;
    render(<TryIt reference="I would like to apply" deviceId={null} kind="upgrade" />);
    expect(seen.at(-1)?.mode).toBe("upgrade");
  });
});
