"use client";
import { useEffect, useRef, type RefObject } from "react";

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * A dialog that behaves (design system, 5-desktop-and-web): it takes focus when it opens (an [autofocus] control, else the
 * first one), Tab and Shift+Tab stay inside it, Esc takes the safe way out ({@code onEscape}), and focus goes back to what
 * opened it when it closes.
 */
export function useDialog(ref: RefObject<HTMLElement | null>, onEscape: () => void) {
  const escape = useRef(onEscape);
  useEffect(() => { escape.current = onEscape; }, [onEscape]);

  useEffect(() => {
    const box = ref.current;
    if (!box) return;
    const opener = document.activeElement as HTMLElement | null;
    const items = () => Array.from(box.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (!box.contains(document.activeElement)) (box.querySelector<HTMLElement>("[autofocus]") ?? items()[0])?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); escape.current(); return; }
      if (e.key !== "Tab") return;
      const list = items();
      if (list.length === 0) return;
      const first = list[0], last = list[list.length - 1];
      const at = document.activeElement;
      if (e.shiftKey && (at === first || !box.contains(at))) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && (at === last || !box.contains(at))) { e.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey, true);
    return () => {
      document.removeEventListener("keydown", onKey, true);
      if (opener && document.contains(opener)) opener.focus();
    };
  }, [ref]);
}
