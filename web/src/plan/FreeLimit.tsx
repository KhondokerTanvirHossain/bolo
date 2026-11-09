"use client";
import { useRef } from "react";
import { useDialog } from "@/ui/useDialog";
import { comeBackDay, limitTitle, type FreeTalks } from "./freeTalks";

/**
 * ScreenFreeLimit (US-8.7): tapping Talk with no free talks left. The title states the fact; the body says when they come
 * back. No plans exist yet, so no "See plans" and no invite offer. Practice stays free.
 */
export function FreeLimit({ freeTalks, onClose }: { freeTalks: FreeTalks | null; onClose: () => void }) {
  const day = freeTalks ? comeBackDay(freeTalks) : "";
  const box = useRef<HTMLDivElement>(null);
  useDialog(box, onClose);
  return (
    <>
      <div className="dk-dim" />
      <div className="dk-card" ref={box} role="dialog" aria-modal="true" aria-labelledby="freelimit-title">
        <h1 className="bo-flow__title" id="freelimit-title">{freeTalks ? limitTitle(freeTalks) : "You've had your free talks for now"}</h1>
        <p className="bo-flow__body">{day ? `They come back on ${day}.` : "They come back soon."} Practising sounds from past feedback stays free.</p>
        <div style={{ display: "grid", gap: 10, marginTop: 20 }}>
          <button className="bo-btn bo-btn--primary bo-btn--block" onClick={onClose} autoFocus>{day ? `Wait for ${day}` : "OK"}</button>
        </div>
      </div>
    </>
  );
}
