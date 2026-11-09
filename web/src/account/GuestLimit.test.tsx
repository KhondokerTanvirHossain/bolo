// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { GuestLimit } from "./GuestLimit";
import type { LearnerView } from "@/api/browser";

const me = (keep: boolean) => ({ learnerId: "g", kind: "guest", consent: { keepRecordings: keep, policyVersion: "v" }, profile: null,
  settings: { buddySpeed: "normal", captions: true }, account: null }) as unknown as LearnerView;

describe("sign in before the first talk", () => {
  afterEach(cleanup);

  it("doesn't promise the Day 1 clip comes along when recordings aren't kept", () => {
    render(<GuestLimit me={me(false)} onSignedIn={() => {}} onClose={() => {}} />);
    expect(screen.queryByText(/Day 1 clip come with you/)).toBeNull();
    expect(screen.getByText(/Your answers come with you\./)).toBeTruthy();
    expect(screen.getByText("Stays on this device, as you chose")).toBeTruthy();
  });

  it("says the Day 1 clip comes along when it's kept", () => {
    render(<GuestLimit me={me(true)} onSignedIn={() => {}} onClose={() => {}} />);
    expect(screen.getByText(/Your answers and your Day 1 clip come with you\./)).toBeTruthy();
  });
});
