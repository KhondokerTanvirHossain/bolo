"use client";
import { useRef, type ReactNode } from "react";
import { useDialog } from "./useDialog";

/** A centred dialog card (dk-card) that keeps focus inside, closes the safe way on Esc and gives focus back (useDialog). */
export function DialogCard({ labelledBy, onEscape, children }: { labelledBy: string; onEscape: () => void; children: ReactNode }) {
  const box = useRef<HTMLDivElement>(null);
  useDialog(box, onEscape);
  return <div className="dk-card" ref={box} role="dialog" aria-modal="true" aria-labelledby={labelledBy}>{children}</div>;
}
