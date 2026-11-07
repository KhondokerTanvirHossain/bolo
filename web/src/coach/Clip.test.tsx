// @vitest-environment jsdom
import { act, render } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { Clip } from "./Clip";

describe("Clip", () => {
  /** Review #2: a parent that keeps the play function in state must not be driven into an endless re-render loop. */
  it("registers its play function once per clip", async () => {
    let registrations = 0;
    function Parent() {
      const [, setPlay] = useState<(() => void) | null>(null);
      return <Clip src="/a.wav" label="Play" playRef={(p) => { registrations++; setPlay(() => p); }} />;
    }
    render(<Parent />);
    await act(async () => { await new Promise((r) => setTimeout(r, 100)); });
    expect(registrations).toBeLessThanOrEqual(2);
  });

  /** Review #1: the next card reuses the component; its audio must follow the new clip. */
  it("plays the new clip when its source changes", () => {
    const { container, rerender } = render(<Clip src="/clip-1.wav" label="Play" />);
    expect(container.querySelector("audio")?.getAttribute("src")).toBe("/clip-1.wav");
    rerender(<Clip src="/clip-2.wav" label="Play" />);
    expect(container.querySelector("audio")?.getAttribute("src")).toBe("/clip-2.wav");
  });
});
