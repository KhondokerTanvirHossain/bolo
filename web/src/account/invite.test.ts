// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { currentInvite, takeInviteFromUrl } from "./invite";

beforeEach(() => {
  window.sessionStorage.clear();
  window.history.replaceState(null, "", "/");
});

describe("closed-beta invite links", () => {
  it("reads ?invite= once, keeps it for the session and tidies the address bar", () => {
    window.history.replaceState(null, "", "/?invite=abc123xyz0&x=1");
    takeInviteFromUrl();
    expect(currentInvite()).toBe("abc123xyz0");
    expect(window.location.search).toBe("?x=1");
  });

  it("ignores a link without one and anything that isn't a code", () => {
    takeInviteFromUrl();
    expect(currentInvite()).toBeNull();
    window.history.replaceState(null, "", "/?invite=<script>");
    takeInviteFromUrl();
    expect(currentInvite()).toBeNull();
  });
});
