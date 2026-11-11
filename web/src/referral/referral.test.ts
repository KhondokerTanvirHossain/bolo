// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { currentRef, inviteLink, shareLink, takeRefFromUrl } from "./referral";

afterEach(() => { vi.restoreAllMocks(); window.sessionStorage.clear(); window.history.replaceState(null, "", "/"); });

describe("takeRefFromUrl", () => {
  it("takeRefFromUrlKeepsAValidCode", () => {
    window.history.replaceState(null, "", "/?ref=ab12cd34&x=1");
    takeRefFromUrl();
    expect(currentRef()).toBe("ab12cd34");
    expect(window.location.search).toBe("?x=1");
  });
  it("ignores a code that isn't 8 letters or digits", () => {
    window.history.replaceState(null, "", "/?ref=<script>");
    takeRefFromUrl();
    expect(currentRef()).toBeNull();
  });
});

describe("shareLink", () => {
  const link = inviteLink("ab12cd34", "https://bolo.eliooo.org");
  it("builds the link from the site's own address", () => {
    expect(link).toBe("https://bolo.eliooo.org/?ref=ab12cd34");
  });
  it("uses the share sheet when there is one", async () => {
    const share = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { share });
    expect(await shareLink(link)).toBe("shared");
    expect(share).toHaveBeenCalledWith({ text: "I'm practising speaking English with Bolo. Try it: " + link });
  });
  it("shareFallsBackToCopy", async () => {
    Object.assign(navigator, { share: undefined, clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
    expect(await shareLink(link)).toBe("copied");
  });
  it("a cancelled share sheet is not an error", async () => {
    Object.assign(navigator, { share: vi.fn().mockRejectedValue(new DOMException("no", "AbortError")) });
    expect(await shareLink(link)).toBe("cancelled");
  });
  it("says so when neither sharing nor copying works", async () => {
    Object.assign(navigator, { share: undefined, clipboard: { writeText: vi.fn().mockRejectedValue(new Error("denied")) } });
    expect(await shareLink(link)).toBe("failed");
  });
});
