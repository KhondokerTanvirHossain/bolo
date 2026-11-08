// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DeleteEverything } from "./DeleteEverything";
import { KeepRecordingsRow } from "./PrivacyScreen";

const posts: { url: string; body: unknown }[] = [];
beforeEach(() => {
  posts.length = 0;
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    posts.push({ url, body: init?.body ? JSON.parse(String(init.body)) : null });
    return new Response(null, { status: 204 });
  }));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("Delete everything (US-8.6)", () => {
  it("needs the word DELETE before the button works", async () => {
    const done = vi.fn();
    render(<DeleteEverything onCancel={() => {}} onDeleted={done} />);
    const button = screen.getByRole("button", { name: "Delete everything" }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("Type DELETE to confirm"), { target: { value: "delete" } });
    expect(button.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("Type DELETE to confirm"), { target: { value: "DELETE" } });
    expect(button.disabled).toBe(false);
    fireEvent.click(button);
    await waitFor(() => expect(done).toHaveBeenCalled());
    expect(posts.find((p) => p.url === "/api/me/delete")?.body).toEqual({ confirm: "DELETE" });
  });
});

describe("Keep my recordings (US-8.5)", () => {
  it("saves the new choice as a consent record, for the next talk", async () => {
    render(<KeepRecordingsRow keep={true} />);
    fireEvent.click(screen.getByRole("switch", { name: "Keep my recordings" }));
    await waitFor(() => expect(posts.find((p) => p.url === "/api/consent")?.body).toMatchObject({ keepRecordings: false, over13: true }));
    expect(screen.getByRole("switch", { name: "Keep my recordings" }).getAttribute("aria-checked")).toBe("false");
    expect(screen.getByText(/from your next talk/)).toBeTruthy();
  });
});
