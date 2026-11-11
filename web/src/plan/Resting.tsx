"use client";
import { DialogCard } from "@/ui/DialogCard";

/** The global daily cap was reached (P2a): kind, plain, no blame; back tomorrow. Practice from past feedback still works. */
export function Resting({ onClose }: { onClose: () => void }) {
  return (
    <>
      <div className="dk-dim" />
      <DialogCard labelledBy="resting-title" onEscape={onClose}>
        <h1 className="bo-flow__title" id="resting-title">Bolo is resting</h1>
        <p className="bo-flow__body">So many people talked today that Bolo needs a break. Back tomorrow. You can still practise sounds from your feedback.</p>
        <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 20 }} onClick={onClose} autoFocus>OK</button>
      </DialogCard>
    </>
  );
}
