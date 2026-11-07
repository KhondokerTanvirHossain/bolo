// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

type Deferred<T> = { promise: Promise<T>; resolve: (v: T) => void };
function deferred<T>(): Deferred<T> {
  let resolve!: (v: T) => void;
  const promise = new Promise<T>((r) => { resolve = r; });
  return { promise, resolve };
}

const recordings: Deferred<unknown>[] = [];
const cancel = vi.fn();
const replies: Deferred<unknown>[] = [];
vi.mock("@/audio/recordPcm", () => ({
  startPcmRecording: vi.fn(() => { const d = deferred<unknown>(); recordings.push(d); return d.promise; }),
}));
vi.mock("./attempts", async (orig) => ({
  ...(await orig<typeof import("./attempts")>()),
  postAttempt: vi.fn(() => { const d = deferred<unknown>(); replies.push(d); return d.promise; }),
}));

import { ReadAloud } from "./ReadAloud";
import { postAttempt } from "./attempts";

const recording = () => ({ stop: async () => new Blob(["x"]), cancel });
const nailed = { kind: "ok", outcome: { level: "NAILED", heard: true, focus: [] } };

afterEach(() => { cleanup(); recordings.length = 0; replies.length = 0; cancel.mockClear(); vi.mocked(postAttempt).mockClear(); });

describe("ReadAloud", () => {
  /** Review: leaving while the mic is still opening must not leave it recording for 15 s in the background. */
  it("cancels a recording that starts after it has gone", async () => {
    const { unmount } = render(<ReadAloud reference="Very good" deviceId={null} />);
    fireEvent.click(screen.getByRole("button", { name: /start recording/i }));
    unmount();
    await act(async () => { recordings[0].resolve(recording()); });
    expect(cancel).toHaveBeenCalled();
  });

  it("cancels the recording when the sentence changes", async () => {
    const { rerender } = render(<ReadAloud reference="Very good" deviceId={null} />);
    fireEvent.click(screen.getByRole("button", { name: /start recording/i }));
    await act(async () => { recordings[0].resolve(recording()); });
    rerender(<ReadAloud reference="The school station" deviceId={null} />);
    expect(cancel).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /start recording/i })).toBeTruthy();
  });

  /** Review: the next sentence must be recordable straight away, not stuck behind the last one's check. */
  it("ignores a late result for the previous sentence", async () => {
    const onOutcome = vi.fn();
    const { rerender } = render(<ReadAloud reference="Very good" deviceId={null} onOutcome={onOutcome} />);
    fireEvent.click(screen.getByRole("button", { name: /start recording/i }));
    await act(async () => { recordings[0].resolve(recording()); });
    await act(async () => { fireEvent.click(screen.getByRole("button", { name: /stop recording/i })); });
    rerender(<ReadAloud reference="The school station" deviceId={null} onOutcome={onOutcome} />);
    const start = screen.getByRole("button", { name: /start recording/i }) as HTMLButtonElement;
    expect(start.disabled).toBe(false);
    await act(async () => { replies[0].resolve(nailed); });
    expect(screen.queryByText("Nailed it")).toBeNull();
    expect(onOutcome).not.toHaveBeenCalled();
    fireEvent.click(start);
    expect(recordings).toHaveLength(2);
  });

  it("sends the mode it was given", async () => {
    render(<ReadAloud reference="Very good" deviceId={null} mode="upgrade" />);
    fireEvent.click(screen.getByRole("button", { name: /start recording/i }));
    await act(async () => { recordings[0].resolve(recording()); });
    await act(async () => { fireEvent.click(screen.getByRole("button", { name: /stop recording/i })); });
    expect(vi.mocked(postAttempt).mock.calls[0][2]).toBe("upgrade");
  });
});
