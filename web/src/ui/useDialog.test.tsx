// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useRef, useState } from "react";
import { useDialog } from "./useDialog";

afterEach(cleanup);

function Sheet({ onEscape }: { onEscape: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useDialog(ref, onEscape);
  return (
    <div ref={ref} role="dialog" aria-modal="true">
      <button>First</button><button>Second</button><button>Last</button>
    </div>
  );
}

function Page({ onEscape }: { onEscape: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Open</button>
      {open && <Sheet onEscape={() => { onEscape(); setOpen(false); }} />}
    </>
  );
}

describe("dialogs keep focus and close with Esc (design: 5-desktop-and-web)", () => {
  it("focuses the first control when it opens", () => {
    render(<Sheet onEscape={vi.fn()} />);
    expect(document.activeElement).toBe(screen.getByText("First"));
  });

  it("Tab and Shift+Tab stay inside", () => {
    render(<Sheet onEscape={vi.fn()} />);
    screen.getByText("Last").focus();
    fireEvent.keyDown(document.activeElement!, { key: "Tab" });
    expect(document.activeElement).toBe(screen.getByText("First"));
    fireEvent.keyDown(document.activeElement!, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(screen.getByText("Last"));
  });

  it("Esc takes the safe way out, and focus returns to what opened it", () => {
    const onEscape = vi.fn();
    render(<Page onEscape={onEscape} />);
    const opener = screen.getByText("Open");
    opener.focus();
    fireEvent.click(opener);
    expect(document.activeElement).toBe(screen.getByText("First"));
    fireEvent.keyDown(document.activeElement!, { key: "Escape" });
    expect(onEscape).toHaveBeenCalledTimes(1);
    expect(document.activeElement).toBe(opener);
  });
});
