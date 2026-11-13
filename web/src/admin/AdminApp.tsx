"use client";
import { useCallback, useEffect, useState } from "react";
import { approvePayment, fetchPayments, fetchRatings, rejectPayment, type AdminPayment, type Ratings } from "./admin";
import { fetchHelp, fetchSettings, fetchUsage, fetchUsers, fetchWaitlist, type Waitlist, grant, helpDone, saveSettings, setUnlimited, type AdminUser, type HelpRequest, type Settings, type Usage } from "./admin";

type Tab = "users" | "payments" | "help" | "waitlist" | "usage" | "ratings" | "settings";
const TAB_NAMES: Record<Tab, string> = { users: "Users", payments: "Payments", help: "Help", waitlist: "Waitlist", usage: "Usage", ratings: "Ratings",
  settings: "Settings" };
const day = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "Asia/Dhaka" }) : "–");

/** The founder's admin portal (P2a): users (unlock, give a plan), usage and cost, settings (daily cap, plans). */
export function AdminApp() {
  const [allowed, setAllowed] = useState<boolean | null>(null);
  const [tab, setTab] = useState<Tab>("users");
  useEffect(() => { void fetchSettings().then((s) => setAllowed(s !== null)); }, []);
  if (allowed === null) return null;
  if (!allowed) return <main className="dk-page"><h1 className="dk-h1">Not found</h1></main>;
  return (
    <main className="dk-page ad-page">
      <h1 className="dk-h1">Bolo admin</h1>
      <div className="ad-tabs" role="tablist">
        {(Object.keys(TAB_NAMES) as Tab[]).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={`bo-chip${tab === t ? " on" : ""}`} onClick={() => setTab(t)}>
            {TAB_NAMES[t]}
          </button>
        ))}
      </div>
      {tab === "users" && <Users />}
      {tab === "help" && <HelpTab />}
      {tab === "payments" && <PaymentsTab />}
      {tab === "waitlist" && <WaitlistTab />}
      {tab === "usage" && <UsageTab />}
      {tab === "ratings" && <RatingsTab />}
      {tab === "settings" && <SettingsTab />}
    </main>
  );
}

function Users() {
  const [q, setQ] = useState("");
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [plan, setPlan] = useState("premium");
  const [days, setDays] = useState(30);
  const [note, setNote] = useState<string | null>(null);
  const load = useCallback(() => { void fetchUsers(q).then(setUsers); }, [q]);
  useEffect(() => { load(); }, [load]);
  return (
    <section>
      <div className="ad-bar">
        <input className="ad-input" aria-label="Search by email or name" placeholder="Search by email or name" value={q} onChange={(e) => setQ(e.target.value)} />
        <label className="ad-label">Give <select className="ad-input" aria-label="Plan to give" value={plan} onChange={(e) => setPlan(e.target.value)}>
          <option value="basic">Basic</option><option value="premium">Premium</option><option value="ultimate">Ultimate</option></select></label>
        <label className="ad-label">for <input className="ad-input ad-num" aria-label="Days" type="number" min={1} max={366} value={days}
          onChange={(e) => setDays(Number(e.target.value))} /> days</label>
      </div>
      {note && <p className="bo-footnote" role="status">{note}</p>}
      <div className="ad-scroll">
        <table className="ad-table">
          <thead><tr><th>Email</th><th>Plan</th><th>Talks this month</th><th>Last talk</th><th>Unlimited</th><th /></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.email}{u.name ? <small>{u.name}</small> : null}</td>
                <td>{u.plan}{u.planEndsAt ? <small>until {day(u.planEndsAt)}</small> : null}</td>
                <td>{u.talksThisMonth}</td>
                <td>{day(u.lastTalkAt)}</td>
                <td><input type="checkbox" aria-label={`Unlimited for ${u.email}`} checked={u.unlimited}
                  onChange={async (e) => { if (await setUnlimited(u.id, e.target.checked)) load(); }} /></td>
                <td><button className="bo-btn bo-btn--secondary ad-small" onClick={async () => {
                  const ok = await grant(u.id, plan, days, "admin");
                  setNote(ok ? `Gave ${u.email} ${plan} for ${days} days.` : "That didn't work.");
                  load();
                }}>Give {plan}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {users.length === 0 && <p className="bo-footnote">No accounts match.</p>}
      </div>
    </section>
  );
}

function HelpTab() {
  const [items, setItems] = useState<HelpRequest[] | null>(null);
  const load = () => { void fetchHelp().then(setItems); };
  useEffect(load, []);
  if (!items) return null;
  return (
    <section>
      {items.length === 0 && <p className="bo-footnote">No open reports.</p>}
      <div className="ad-scroll">
        <table className="ad-table">
          <thead><tr><th>When</th><th>Who</th><th>What went wrong</th><th>Context</th><th /></tr></thead>
          <tbody>{items.map((h) => (
            <tr key={h.id}><td>{day(h.at)}</td><td>{h.email ?? "guest"}</td><td>{h.message}</td>
              <td><small>{Object.entries(h.context).map(([k, v]) => `${k}: ${v}`).join(" · ")}</small></td>
              <td><button className="bo-btn bo-btn--secondary ad-small" onClick={async () => { if (await helpDone(h.id)) load(); }}>Done</button></td></tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  );
}

const REASONS = ["TrxID not found", "Amount doesn't match", "Other"];

/** P2e: check each TrxID in the bKash app's statement, then Approve (30 days of the plan) or Reject with a reason. */
function PaymentsTab() {
  const [list, setList] = useState<AdminPayment[] | null | undefined>(undefined);
  const [reason, setReason] = useState(REASONS[0]);
  const [note, setNote] = useState<string | null>(null);
  const load = useCallback(() => { void fetchPayments().then(setList); }, []);
  useEffect(load, [load]);
  if (list === undefined) return null;
  if (list === null) return <p className="bo-footnote" role="alert">Payments didn&apos;t load. Reload to try again.</p>;
  const when = (iso: string) => new Date(iso).toLocaleString("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Asia/Dhaka" });
  const act = async (ok: Promise<boolean>, what: string) => { setNote((await ok) ? what : "That didn't go through (maybe already decided). Reloaded."); load(); };
  const pending = list.filter((p) => p.status === "pending").length;
  return (
    <section>
      <div className="ad-bar">
        <p className="dk-sub">{pending === 0 ? "Nothing to check." : `${pending} to check. Find each TrxID in your bKash statement first.`}</p>
        <label className="ad-label">Reject reason <select className="ad-input" aria-label="Reject reason" value={reason} onChange={(e) => setReason(e.target.value)}>
          {REASONS.map((r) => <option key={r}>{r}</option>)}</select></label>
      </div>
      {note && <p className="bo-footnote" role="status">{note}</p>}
      <div className="ad-scroll">
        <table className="ad-table">
          <thead><tr><th>When</th><th>Who</th><th>Plan</th><th>Amount</th><th>TrxID</th><th>Paid from</th><th>Status</th><th /></tr></thead>
          <tbody>{list.map((p) => (
            <tr key={p.id}><td>{when(p.at)}</td><td>{p.email ?? "–"}</td><td>{p.plan}</td><td>৳{p.amountBdt}</td><td>{p.trxId}</td>
              <td>{p.payerNumber}</td><td>{p.status}{p.reason ? <small>{p.reason}</small> : null}</td>
              <td>{p.status === "pending" && (
                <span style={{ display: "inline-flex", gap: 6 }}>
                  <button className="bo-btn bo-btn--primary ad-small" onClick={() => void act(approvePayment(p.id), `Approved ${p.trxId}: ${p.plan} for 30 days.`)}>Approve</button>
                  <button className="bo-btn bo-btn--secondary ad-small" onClick={() => void act(rejectPayment(p.id, reason), `Rejected ${p.trxId}.`)}>Reject</button>
                </span>)}</td></tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  );
}

function WaitlistTab() {
  const [w, setW] = useState<Waitlist | null | undefined>(undefined);
  useEffect(() => { void fetchWaitlist().then(setW); }, []);
  if (w === undefined) return null;
  if (w === null) return <p className="bo-footnote" role="alert">The waitlist didn&apos;t load. Reload to try again.</p>;
  const title = (p: string) => p.charAt(0).toUpperCase() + p.slice(1);
  const when = (iso: string) => new Date(iso).toLocaleString("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Asia/Dhaka" });
  return (
    <section>
      {w.counts.length === 0 ? <p className="bo-footnote">Nobody is waiting yet.</p> : (
        <ul className="dk-sub" style={{ listStyle: "none", padding: 0 }}>
          {w.counts.map((c) => <li key={c.plan}>{title(c.plan)}: {c.bd + c.intl} (৳ {c.bd}, $ {c.intl})</li>)}
        </ul>
      )}
      <div className="ad-scroll">
        <table className="ad-table">
          <thead><tr><th>When</th><th>Who</th><th>Plan</th><th>Pays in</th><th>Time zone</th></tr></thead>
          <tbody>{w.entries.map((e, i) => (
            <tr key={`${e.email ?? "?"}-${i}`}><td>{when(e.at)}</td><td>{e.email ?? "–"}</td><td>{title(e.plan)}</td>
              <td>{e.region === "bd" ? "৳" : "$"}</td><td>{e.timeZone ?? "–"}</td></tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  );
}

function UsageTab() {
  const [u, setU] = useState<Usage | null>(null);
  useEffect(() => { void fetchUsage(14).then(setU); }, []);
  if (!u) return null;
  return (
    <section>
      <p className="dk-sub" data-usage-now>Live talks now: {u.now.live} · Today: {u.now.today} of {u.now.cap} (daily cap)</p>
      <div className="ad-scroll">
        <table className="ad-table">
          <thead><tr><th>Day</th><th>Talks</th><th>Minutes</th><th>New accounts</th><th>Est. cost (USD)</th></tr></thead>
          <tbody>{u.days.map((d) => (
            <tr key={d.date}><td>{day(d.date)}</td><td>{d.talks}</td><td>{d.minutes}</td><td>{d.newAccounts}</td><td>${Number(d.costUsd).toFixed(2)}</td></tr>
          ))}</tbody>
        </table>
      </div>
      <p className="bo-footnote">Cost is an estimate from the usage ledger (speech, voice, Claude), not a bill.</p>
    </section>
  );
}

/** DBF-8: "How was today?" — the PRD goal is at least 70% of ratings at 4–5. */
function RatingsTab() {
  const [r, setR] = useState<Ratings | null>(null);
  useEffect(() => { void fetchRatings().then(setR); }, []);
  if (!r) return null;
  return (
    <section>
      <p className="dk-sub">Average (30 days): {r.average30 == null ? "none yet" : `${r.average30.toFixed(1)} of 5`}</p>
      <p className="dk-sub">Rated 4–5: {r.share45 == null ? "none yet" : `${Math.round(r.share45 * 100)}%`} (goal 70%)</p>
      <p className="dk-sub">Ratings (30 days): {r.count30}</p>
      <div className="ad-scroll">
        <table className="ad-table">
          <thead><tr><th>Day</th><th>Score</th><th>Note</th></tr></thead>
          <tbody>{r.latest.map((n, i) => (
            <tr key={i}><td>{day(n.at)}</td><td>{n.score}</td><td>{n.note}</td></tr>
          ))}</tbody>
        </table>
      </div>
      {r.latest.length === 0 && <p className="bo-footnote">No notes yet.</p>}
    </section>
  );
}

function SettingsTab() {
  const [s, setS] = useState<Settings | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [capText, setCapText] = useState("");
  // Review (P2e): only send the bKash number when it was edited here, so an old tab can't switch bKash off.
  const [bkashEdited, setBkashEdited] = useState(false);
  useEffect(() => { void fetchSettings().then((x) => { setS(x); if (x) setCapText(String(x.dailyTalkCap)); }); }, []);
  if (!s) return null;
  // Review (P2a): an emptied field is not 0 (0 rests everyone), and 0 itself needs a yes.
  const save = async () => {
    const cap = capText.trim() === "" ? NaN : Number(capText);
    if (!Number.isInteger(cap) || cap < 0) { setNote("Enter a daily cap (0 or more)."); return; }
    if (cap === 0 && !window.confirm("A cap of 0 means: No one can start a talk until you raise it. Save?")) { setNote("Not saved."); return; }
    const { bkashNumber, ...rest } = s;
    const body = bkashEdited ? { ...rest, dailyTalkCap: cap, bkashNumber: bkashNumber ?? "" } : { ...rest, dailyTalkCap: cap };
    setNote((await saveSettings(body)) ? "Saved." : "That didn't save (check the bKash number: 11 digits, 01…).");
  };
  const setPlan = (i: number, k: "monthlyTalks" | "priceBdt" | "priceUsd", v: number) =>
    setS({ ...s, plans: s.plans.map((p, j) => (j === i ? { ...p, [k]: v } : p)) });
  return (
    <section>
      <label className="ad-label">Daily talk cap (everyone, except unlimited)
        <input className="ad-input ad-num" type="number" min={0} aria-label="Daily talk cap" value={capText}
          onChange={(e) => setCapText(e.target.value)} /></label>
      <label className="ad-label">bKash number for payments
        <input className="ad-input" aria-label="bKash number for payments" inputMode="numeric" placeholder="01XXXXXXXXX (empty: bKash off)"
          value={s.bkashNumber ?? ""} onChange={(e) => { setBkashEdited(true); setS({ ...s, bkashNumber: e.target.value.trim() }); }} /></label>
      <div className="ad-scroll">
        <table className="ad-table">
          <thead><tr><th>Plan</th><th>Talks a month</th><th>Price (৳)</th><th>Price ($)</th></tr></thead>
          <tbody>{s.plans.map((p, i) => (
            <tr key={p.plan}><td>{p.plan}</td>
              <td><input className="ad-input ad-num" type="number" aria-label={`${p.plan} talks a month`} value={p.monthlyTalks} onChange={(e) => setPlan(i, "monthlyTalks", Number(e.target.value))} /></td>
              <td><input className="ad-input ad-num" type="number" aria-label={`${p.plan} price in taka`} value={p.priceBdt} onChange={(e) => setPlan(i, "priceBdt", Number(e.target.value))} /></td>
              <td><input className="ad-input ad-num" type="number" step="0.5" aria-label={`${p.plan} price in dollars`} value={p.priceUsd} onChange={(e) => setPlan(i, "priceUsd", Number(e.target.value))} /></td>
            </tr>))}</tbody>
        </table>
      </div>
      <button className="bo-btn bo-btn--primary" onClick={() => void save()}>Save</button>
      {note && <p className="bo-footnote" role="status">{note}</p>}
    </section>
  );
}
