"use client";
import Link from "next/link";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { api, type LearnerView } from "@/api/browser";
import { fetchMe, signInWithGoogle } from "@/account/session";
import { fetchPlans, joinWaitlist, myWaitlist, planTitle, priceLabel, regionGuess, saveRegion, type MyWaitlist, type PaidPlan, type Region } from "./pricing";

/**
 * The pricing page (P2c; ScreenPlans, US-8.8): Free first and dashed, then the paid plans, each with its talks as a number.
 * Basic is preselected. ৳ in Bangladesh, $ elsewhere; payments aren't open yet, so the button joins a waitlist.
 */
export function PricingScreen() {
  const [plans, setPlans] = useState<PaidPlan[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [region, setRegion] = useState<Region>("intl");
  const [chosen, setChosen] = useState("basic");
  const [me, setMe] = useState<LearnerView | null>(null);
  const [myPlan, setMyPlan] = useState<string | null>(null);
  const [listed, setListed] = useState<MyWaitlist>(null);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const radios = useRef<Record<string, HTMLButtonElement | null>>({});

  const loadMe = async (who: LearnerView | null) => {
    setMe(who);
    if (!who) return;
    if (who.kind !== "account") { setMyPlan("free"); return; }
    const home = await api.get<{ freeTalks?: { plan?: string } | null }>("/api/home");
    setMyPlan(home.kind === "ok" ? (home.value.freeTalks?.plan ?? "free") : "free");
    setListed(await myWaitlist());
  };

  useEffect(() => {
    setRegion(regionGuess());
    void fetchPlans().then((p) => (p ? setPlans(p) : setFailed(true)));
    void fetchMe().then(loadMe);
  }, []);

  const flip = () => {
    const next: Region = region === "bd" ? "intl" : "bd";
    saveRegion(next);
    setRegion(next);
  };

  const join = async (who: LearnerView | null = me) => {
    if (who?.kind !== "account") return;
    setBusy(true);
    const r = await joinWaitlist(chosen, region);
    setBusy(false);
    if (r === "ok") { setListed({ plan: chosen, region }); setNote(null); }
    else setNote(r === "sign-in" ? "Sign in with Google to join the list." : "That didn't go through. Check your connection, then try again.");
  };

  const signIn = async () => {
    setBusy(true);
    const r = await signInWithGoogle();
    setBusy(false);
    if (r.kind === "ok") { await loadMe(r.learner); await join(r.learner); }
    else if (r.kind !== "cancelled") setNote("Sign-in didn't work. Try again.");
  };

  const paid = plans ?? [];
  const order = paid.map((p) => p.plan);
  const onKey = (e: KeyboardEvent) => {
    const step = e.key === "ArrowDown" || e.key === "ArrowRight" ? 1 : e.key === "ArrowUp" || e.key === "ArrowLeft" ? -1 : 0;
    if (!step || order.length === 0) return;
    e.preventDefault();
    const next = order[(order.indexOf(chosen) + step + order.length) % order.length];
    setChosen(next);
    radios.current[next]?.focus();
  };

  const pick = paid.find((p) => p.plan === chosen);
  const name = planTitle(chosen);
  const onList = listed?.plan === chosen;
  const account = me?.kind === "account";

  return (
    // The app root fills the screen and doesn't scroll (the talk scenes need that), so this page scrolls itself.
    <div style={{ height: "100%", overflowY: "auto" }}>
      <main className="bo-pricing">
        <Link className="bo-btn bo-btn--quiet-neutral bo-pricing__back" href="/">Back to Bolo</Link>
        <h1 className="bo-flow__title">Choose a plan</h1>
        <p className="bo-flow__body">Each talk is up to 5 minutes, then your coach&apos;s feedback.</p>
        {failed && <p className="bo-footnote" role="alert">Plans didn&apos;t load. Check your connection, then reload.</p>}
        {plans && (
          <>
            <div className="bo-plans" role="radiogroup" aria-label="Plans" onKeyDown={onKey} style={{ marginTop: 20 }}>
              <button type="button" role="radio" aria-checked="false" aria-disabled="true" tabIndex={-1} className="bo-plan bo-plan--current">
                <span className="bo-plan__body">
                  <span className="bo-plan__name">Free{myPlan === "free" && <span className="bo-tag">Your plan</span>}</span>
                  <span className="bo-plan__meta">7 talks in your first week, then 2 a month</span>
                </span>
                <span className="bo-plan__price">{region === "bd" ? "৳0" : "$0"}</span>
              </button>
              {paid.map((p) => (
                <button key={p.plan} type="button" role="radio" aria-checked={chosen === p.plan} tabIndex={chosen === p.plan ? 0 : -1}
                  ref={(el) => { radios.current[p.plan] = el; }} className="bo-plan" onClick={() => setChosen(p.plan)}>
                  <span className="bo-plan__body">
                    <span className="bo-plan__name">{planTitle(p.plan)}{myPlan === p.plan && <span className="bo-tag">Your plan</span>}</span>
                    <span className="bo-plan__meta">{p.monthlyTalks} talks a month</span>
                  </span>
                  <span className="bo-plan__price">{priceLabel(p, region)}</span>
                </button>
              ))}
            </div>
            <p className="bo-footnote" style={{ marginTop: 12 }}>Every plan has every feature: talks, your coach&apos;s feedback, practice and your full history.</p>
            <button type="button" className="bo-btn bo-btn--quiet-neutral bo-pricing__switch" onClick={flip}>
              {region === "bd" ? "Not in Bangladesh? See prices in $" : "In Bangladesh? See prices in ৳"}
            </button>

            <div className="bo-pricing__act">
              <p className="bo-flow__body">
                {region === "bd"
                  ? "bKash payments open in a few days. Join the list and we'll email you first."
                  : "Card payments are coming soon. Join the waitlist and we'll email you when they open."}
              </p>
              {onList && account ? (
                <p className="bo-flow__body" role="status">You&apos;re on the list for {name}. We&apos;ll email you at {me?.account?.email}.</p>
              ) : (
                <>
                  {/* The button carries the choice and the price for everyone (ScreenPlans); without an account it signs in first. */}
                  <button type="button" className="bo-btn bo-btn--primary bo-btn--block" disabled={busy} onClick={() => void (account ? join() : signIn())}>
                    {listed ? `Change to ${name}` : region === "bd" && pick ? `Join the list for ${name} · ${priceLabel(pick, "bd")}` : `Join the waitlist for ${name}`}
                  </button>
                  {!account && <p className="bo-footnote">Sign in with Google to join the list.</p>}
                </>
              )}
              {note && <p className="bo-footnote" role="alert">{note}</p>}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
