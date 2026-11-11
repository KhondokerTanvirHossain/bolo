"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { copyText, fetchReferral, inviteLink, shareLink, type Referral } from "./referral";

const day = (iso: string) => new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "long", timeZone: "Asia/Dhaka" });

/**
 * Me → Invite friends (P2d; US-8.10, Premium per the founder): 3 slots, filled by the first names of friends who finished a
 * talk; the link with Copy and Share. Bolo never reads contacts or sends anything itself.
 */
export function InviteScreen({ onBack }: { onBack: () => void }) {
  const [r, setR] = useState<Referral | null | undefined>(undefined);
  const [note, setNote] = useState<string | null>(null);
  useEffect(() => { void fetchReferral().then(setR); }, []);

  if (r === undefined) return null;
  if (r === null) {
    return (
      <section>
        <p className="bo-footnote" role="alert">Your invite didn&apos;t load. Check your connection, then try again.</p>
        <button className="bo-btn bo-btn--secondary" onClick={onBack}>Back</button>
      </section>
    );
  }
  const link = inviteLink(r.code, window.location.origin);
  const set = r.counted % 3;
  const all = r.rewards >= r.maxRewards;
  // The current set of 3: names already counted towards earlier months aren't shown again.
  const shown = all ? r.friends.slice(-3) : r.friends.slice(r.friends.length - set);
  const copy = async () => setNote((await copyText(link)) ? "Link copied" : "Copying didn't work. Select the link and copy it.");
  const share = async () => {
    const out = await shareLink(link);
    if (out === "copied") setNote("Link copied");
    else if (out === "failed") setNote("Copying didn't work. Select the link and copy it.");
    else setNote(null);
  };

  return (
    <section className="bo-invite">
      <button className="bo-back" onClick={onBack}><Icon name="chevron_left" />Me</button>
      <h1 className="dk-h1">{r.offer || r.rewards > 0 ? "Invite 3 friends, get a month of Premium" : "Invite friends"}</h1>
      {r.premiumUntil && <p className="dk-sub" role="status">A month of Premium is yours. Until {day(r.premiumUntil)}.</p>}
      {all ? (
        <p className="dk-sub">You&apos;ve had all {r.maxRewards} free months. Thank you for sharing Bolo.</p>
      ) : r.offer ? (
        <>
          <p className="dk-sub">When 3 friends you invite each finish a talk, you get 30 days of Premium. Bolo never reads your contacts or messages anyone.</p>
          <ol className="bo-invite__slots" aria-label="Friends who finished a talk">
            {[0, 1, 2].map((i) => (
              <li key={i} className={shown[i] ? "on" : undefined}>
                <Icon name={shown[i] ? "check_circle" : "person_add"} />{shown[i] ?? "Waiting for a friend"}
              </li>
            ))}
          </ol>
        </>
      ) : (
        <p className="dk-sub">The invite offer has ended. Friends who already joined still count.</p>
      )}
      {r.offer && !all && (
        <>
          <label className="ad-label bo-invite__link">Your invite link
            <input className="ad-input" readOnly value={link} onFocus={(e) => e.currentTarget.select()} />
          </label>
          <div className="bo-invite__actions">
            <button className="bo-btn bo-btn--secondary" onClick={() => void copy()}>Copy</button>
            <button className="bo-btn bo-btn--primary" onClick={() => void share()}>Share invite link</button>
          </div>
        </>
      )}
      {note && <p className="bo-footnote" role="status">{note}</p>}
    </section>
  );
}
