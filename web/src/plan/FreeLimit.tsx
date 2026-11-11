"use client";
import Link from "next/link";
import { useRef } from "react";
import { useDialog } from "@/ui/useDialog";
import { comeBackDay, limitTitle, type FreeTalks } from "./freeTalks";

/**
 * ScreenFreeLimit (US-8.7): tapping Talk with no free talks left. The title states the fact; the body says when they come
 * back, and "See plans" (P2c) opens the pricing page. No invite offer yet (P2d). Practice stays free.
 */
export function FreeLimit({ freeTalks, onClose, onInvite }: { freeTalks: FreeTalks | null; onClose: () => void; onInvite?: () => void }) {
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
          <Link className="bo-btn bo-btn--secondary bo-btn--block" href="/pricing">See plans</Link>
          {onInvite && <button className="bo-btn bo-btn--secondary bo-btn--block" onClick={onInvite}>Invite 3 friends, get a free month</button>}
          <button className="bo-btn bo-btn--primary bo-btn--block" onClick={onClose} autoFocus>{day ? `Wait for ${day}` : "OK"}</button>
        </div>
      </div>
    </>
  );
}
