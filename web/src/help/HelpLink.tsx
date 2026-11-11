"use client";
import { useState } from "react";
import { HelpDialog } from "./HelpDialog";
import type { HelpContext } from "./help";

/**
 * Review (P2b): "Trouble? Report a problem" where learners get stuck (the mic step, a talk that can't start), so a phone
 * learner with no sidebar and no Me tab yet is never at a dead end (PRD R7).
 */
export function HelpLink({ ctx, className = "bo-btn bo-btn--quiet-neutral" }: { ctx: HelpContext; className?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" className={className} onClick={() => setOpen(true)}>Trouble? Report a problem</button>
      {open && <HelpDialog ctx={ctx} onClose={() => setOpen(false)} />}
    </>
  );
}
