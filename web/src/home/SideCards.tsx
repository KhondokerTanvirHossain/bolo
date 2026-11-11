"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { counterLine, freeWeekOverNote, type FreeTalks } from "@/plan/freeTalks";
import { HelpDialog } from "@/help/HelpDialog";
import type { HelpContext } from "@/help/help";

/** The free-plan line (ScreenHomeFree): "4 of 7 free talks left in your first week"; once, the day the week ends, the note. */
export function useFreeLine(freeTalks: FreeTalks | null | undefined): string | null {
  const [note, setNote] = useState<string | null>(null);
  const asked = useRef(false);
  // Decided once, when the plan first arrives (the note marks itself seen; never during render).
  useEffect(() => {
    if (!freeTalks || asked.current) return;
    asked.current = true;
    setNote(freeWeekOverNote(freeTalks));
  }, [freeTalks]);
  return freeTalks ? note ?? counterLine(freeTalks) : null;
}

/** The sidebar's cards (DesktopHome): what's left of the free plan (not for unlimited learners) and Bolo on a phone. */
export function SideCards({ freeLine, plan, help }: { freeLine: string | null; plan?: string | null; help?: HelpContext }) {
  const [asking, setAsking] = useState(false);
  return (
    <>
      {freeLine && <div className="dk-side__card" data-side-plan><b>{plan ?? "Free"} plan</b><span>{freeLine}</span>
        <Link className="dk-link" href="/pricing">See plans</Link></div>}
      <div className="dk-side__card"><b>Bolo on your phone</b>
        <span>Open bolo.eliooo.org in your phone&apos;s browser and sign in with the same Google account.</span></div>
      {help && (
        <div className="dk-side__card"><b>Need help?</b>
          <button className="dk-link" style={{ border: 0, background: "transparent", padding: 0 }} onClick={() => setAsking(true)}>Report a problem</button></div>
      )}
      {asking && help && <HelpDialog ctx={help} onClose={() => setAsking(false)} />}
    </>
  );
}
