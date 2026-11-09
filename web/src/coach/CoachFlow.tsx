"use client";
import { useEffect, useRef, useState } from "react";
import { TryIt } from "./TryIt";
import { Icon } from "@/scene/Scene";
import { Clip } from "./Clip";
import { fetchDebrief, pollDebrief, disputeFix, type Debrief, type Fix, type Level } from "./debrief";
import { highlight } from "./highlight";
import { deleteRecording } from "@/progress/progress";
import { api } from "@/api/browser";
import { track } from "@/analytics/track";
import { fetchChat, type Chat } from "./chat";
import { ChatScreen } from "./ChatScreen";

type Props = {
  talkId: string; deviceId?: string | null; onTalkAgain: () => void; onDone: () => void; onNoDebrief?: () => void;
  /** A ready debrief was shown (the talk counts as finished for the first loop and the guest save prompts). */
  onReady?: () => void;
  /** US-6.8: the first debrief's last card offers "Practise (3 min)" on its first sound (or the sounds list). */
  onPractise?: (pattern?: string) => void;
  /** ScreenHistorySession: a past talk — Practise these (3 min) / Back, and a quiet Delete recording (US-8.6). */
  history?: boolean;
};

const LEVEL_WORD: Record<Level, string> = { BUILDING: "Building", GOOD: "Good", STRONG: "Strong" };
const LEVEL_BARS: Record<Level, number> = { BUILDING: 1, GOOD: 2, STRONG: 3 };

function seconds(ms: number): string {
  return (ms / 1000).toFixed(1);
}

/** After a talk: "Your coach is listening back", then the debrief (DBF-1..5), or the too-short / failed states. */
export function CoachFlow({ talkId, deviceId = null, onTalkAgain, onDone, onNoDebrief, onReady, onPractise, history = false }: Props) {
  const [debrief, setDebrief] = useState<Debrief | null>({ status: "scoring" });

  useEffect(() => {
    let cancelled = false;
    void pollDebrief({ fetch: () => fetchDebrief(talkId), now: () => Date.now(),
      sleep: (ms) => new Promise((r) => setTimeout(r, ms)) }, (d) => { if (!cancelled) setDebrief(d); }, () => cancelled)
      .then((d) => { if (!cancelled && d === null) setDebrief(null); });
    return () => { cancelled = true; };
  }, [talkId]);

  useEffect(() => { if (debrief === null) onNoDebrief?.(); }, [debrief, onNoDebrief]);

  // Home stops offering "Finish your feedback" once a ready debrief has been seen (US-6.9); counted once per talk.
  const seen = useRef<string | null>(null);
  useEffect(() => {
    if (debrief?.status !== "ready" || seen.current === talkId) return;
    seen.current = talkId;
    // Reopening a past talk from History is not a new debrief view (the debrief-completion metric).
    if (!history) {
      void api.post<void>(`/api/talk/${talkId}/debrief/viewed`);
      void track("debrief_viewed", { fixes_count: debrief.fixes?.length ?? 0 });
    }
    onReady?.();
  }, [debrief, talkId, onReady, history]);

  if (debrief === null) return null;
  if (debrief.status === "scoring") return <Scoring />;
  if (debrief.status === "too_short") {
    return <Notice title="Not enough talking to give you useful feedback yet"
      body="A few more minutes of talking lets your coach find what will help most."
      primary="Talk a bit more" onPrimary={onTalkAgain} onDone={onDone} />;
  }
  if (debrief.status === "failed") {
    return <Notice title="We couldn't prepare your feedback. Your talk is saved." body="Try another talk in a moment."
      primary="Talk again" onPrimary={onTalkAgain} onDone={onDone} />;
  }
  return <DebriefScreen key={String(debrief.recordingDeleted ?? false)} talkId={talkId} deviceId={deviceId} debrief={debrief}
    onTalkAgain={onTalkAgain} onDone={onDone} onPractise={onPractise} history={history}
    onRecordingDeleted={() => void fetchDebrief(talkId).then((d) => { if (d) setDebrief(d); })} />;
}

function Scoring() {
  const heights = [10, 18, 28, 40, 30, 22, 36, 48, 34, 20, 26, 40, 30, 16, 10];
  return (
    <section className="dk-screen on" data-screen="scoring">
      <div className="dk-talk" style={{ display: "flex", alignItems: "center", justifyContent: "center" }}>
        <div className="bo-state" role="status">
          <div className="bo-listenback" aria-hidden="true">
            {heights.map((h, i) => <i key={i} style={{ height: h, animationDelay: `${(i * 0.08).toFixed(2)}s` }} />)}
          </div>
          <p className="bo-state__title">Your coach is listening back</p>
          <p className="body" style={{ margin: 0, color: "var(--ink-secondary)" }}>Picking the three things that will help most.</p>
        </div>
      </div>
    </section>
  );
}

function Notice({ title, body, primary, onPrimary, onDone }: { title: string; body: string; primary: string; onPrimary: () => void; onDone: () => void }) {
  return (
    <section className="dk-screen on" data-screen="scoring">
      <div className="dk-talk">
        <div className="dk-card" role="status">
          <h1 className="bo-flow__title">{title}</h1>
          <p className="bo-flow__body">{body}</p>
          <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 16 }} onClick={onPrimary} autoFocus>{primary}</button>
          <button className="bo-btn bo-btn--secondary bo-btn--block" style={{ marginTop: 10 }} onClick={onDone}>Done</button>
        </div>
      </div>
    </section>
  );
}

function DebriefScreen({ talkId, deviceId, debrief, onTalkAgain, onDone, onPractise, history = false, onRecordingDeleted }:
  { talkId: string; debrief: Debrief; onRecordingDeleted?: () => void } & Omit<Props, "talkId">) {
  const [askDelete, setAskDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteFailed, setDeleteFailed] = useState(false);
  const removeRecording = async () => {
    setDeleting(true);
    setDeleteFailed(false);
    const ok = await deleteRecording(talkId);
    setDeleting(false);
    if (!ok) { setDeleteFailed(true); return; }
    setAskDelete(false);
    onRecordingDeleted?.();
  };
  const hasAnyClip = Boolean(debrief.strength?.clip) || (debrief.fixes ?? []).some((f) => f.clip);
  // From the server's flags, never from missing links: a talk can lack clips for other reasons.
  const audioNote = debrief.recordingDeleted ? "Recording deleted. Feedback and words are still here."
    : debrief.recordingNotKept ? "Recording not kept, as you chose. Feedback and words are still here."
    : debrief.recordingExpired ? "Recordings are kept for 90 days. Feedback and words are still here." : null;
  const [fixes, setFixes] = useState<Fix[]>(debrief.fixes ?? []);
  const [at, setAt] = useState(0);
  // DBF-6: the full chat replaces the card pane; a marked spot goes back to its fix card.
  const [chat, setChat] = useState<Chat | null>(null);
  const [chatMissing, setChatMissing] = useState(false);
  // The pane scrolls; switching between the chat and a card starts the new view at its top.
  const pane = useRef<HTMLDivElement | null>(null);
  const toTop = () => { if (pane.current) pane.current.scrollTop = 0; };
  const go = (i: number) => { setChat(null); setChatMissing(false); setAt(i); toTop(); };
  const openChat = async () => {
    setChatMissing(false);
    const c = await fetchChat(talkId);
    if (c) { setChat(c); toTop(); } else setChatMissing(true);
  };
  const [note, setNote] = useState<string | null>(null);
  const [disputing, setDisputing] = useState(false);
  // The current card's "play my clip" (Space). A ref, not state: registering it must not re-render the screen.
  const playMine = useRef<(() => void) | null>(null);
  const cards = 1 + fixes.length + 1;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      // Esc: closes the chat first, then leaves the feedback (prototype: Esc goes Home). A dialog handles its own Esc.
      if (e.key === "Escape") {
        e.preventDefault();
        if (askDelete) setAskDelete(false);
        else if (chat) { setChat(null); toTop(); }
        else onDone();
        return;
      }
      if (askDelete) return;
      // While the full chat shows, the keys belong to it (scrolling), not to the hidden cards.
      if (chat) return;
      if (e.key === "ArrowDown") { e.preventDefault(); setAt((a) => Math.min(cards - 1, a + 1)); setNote(null); }
      else if (e.key === "ArrowUp") { e.preventDefault(); setAt((a) => Math.max(0, a - 1)); setNote(null); }
      else if (e.key === " " && playMine.current) { e.preventDefault(); playMine.current(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [cards, chat, askDelete, onDone]);

  /** By the fix's stable id, once: a double tap can't remove a second card. */
  const dispute = async (fix: Fix) => {
    if (disputing || !fix.id) return;
    setDisputing(true);
    const done = await disputeFix(talkId, fix.id);
    setDisputing(false);
    if (!done) return;
    setFixes((f) => f.filter((x) => x.id !== fix.id));
    setNote("Thanks, we've removed it");
  };
  const register = (play: () => void) => { playMine.current = play; };

  return (
    <section className="dk-screen on" data-screen="debrief">
      <div className="dk-deb">
        <div className="dk-deb__list">
          <div className="bo-eyebrow bo-eyebrow--coach" style={{ padding: "0 14px 12px" }}><span className="bo-coach-mark">C</span>Your coach · {history ? "this talk" : "today\u2019s talk"}</div>
          <Item n={<Icon name="star" fill size="sm" />} title="Something you did well" sub="Strength" on={at === 0} onClick={() => go(0)} />
          {fixes.map((f, i) => (
            <Item key={`${f.kind}-${i}-${f.title}`} n={String(i + 1)} title={f.title} sub={subtitle(f)} on={at === i + 1} onClick={() => go(i + 1)} />
          ))}
          <Item n={<Icon name="insights" size="sm" />} title={debrief.firstSession ? "Your starting point" : "Your skills today"}
            sub="Skills and what's next" on={at === cards - 1} onClick={() => go(cards - 1)} />
          <div className="dk-deb__foot">
            <button className="bo-btn bo-btn--quiet-neutral" style={{ padding: 0 }} onClick={onDone}><Icon name="close" size="sm" />{history ? "Back" : "Close feedback"}</button>
            <button className="bo-btn bo-btn--quiet-neutral" style={{ padding: 0, display: "flex" }} onClick={() => void openChat()}>
              <Icon name="forum" size="sm" />See full chat</button>
            {history && hasAnyClip && (
              <button className="bo-btn bo-btn--quiet-neutral" style={{ padding: 0, display: "flex" }} onClick={() => setAskDelete(true)}>
                <Icon name="delete" size="sm" />Delete recording</button>
            )}
          </div>
        </div>
        <div className="dk-deb__pane" ref={pane}>
          {note && <p className="bo-footnote" role="status" style={{ textAlign: "center", marginBottom: 12 }}>{note}</p>}
          {audioNote && <p className="bo-footnote" style={{ textAlign: "center", marginBottom: 12 }}>{audioNote}</p>}
          {chatMissing && <p className="bo-footnote" role="status" style={{ textAlign: "center", marginBottom: 12 }}>The chat isn&apos;t available for this talk.</p>}
          {chat ? <ChatScreen chat={chat} onBack={() => setChat(null)} onOpenFix={(n) => go(n)} /> : (<>
          {at === 0 && (
            <div className="dk-detail on">
              <div className="bo-card" style={{ background: "var(--hills-soft)", boxShadow: "none", display: "flex", flexDirection: "column", gap: 16, padding: 28 }}>
                <div className="bo-eyebrow bo-eyebrow--coach"><span className="bo-coach-mark">C</span>Your coach · something you did well</div>
                <p className="title-1" style={{ margin: 0 }}>{debrief.strength?.text}</p>
                <Clip src={debrief.strength?.clip} label="Hear that moment" playRef={register} />
              </div>
            </div>
          )}
          {at > 0 && at <= fixes.length && (
            <FixCard key={fixes[at - 1].id ?? at} fix={fixes[at - 1]} n={at} of={fixes.length} disputing={disputing} deviceId={deviceId ?? null}
              onDispute={() => void dispute(fixes[at - 1])} playRef={register} />
          )}
          {at === cards - 1 && <Skills debrief={debrief} onTalkAgain={onTalkAgain} onDone={onDone} onPractise={onPractise} history={history} />}
          <div className="dk-deb__nav"><span><span className="dk-kbd">↑</span> <span className="dk-kbd">↓</span> move between cards</span><span><span className="dk-kbd">Space</span> plays your clip</span></div>
          </>)}
        </div>
      </div>
      {askDelete && (
        <>
          <div className="dk-dim" />
          <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="delrec-title">
            <h1 className="bo-flow__title" id="delrec-title">Delete this recording?</h1>
            <p className="bo-flow__body">Your voice from this talk goes at once. The feedback and words stay.</p>
            {deleteFailed && <p className="bo-footnote" role="alert">Couldn&apos;t delete it. Try again.</p>}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 18 }}>
              <button className="bo-btn bo-btn--primary" onClick={() => { setAskDelete(false); setDeleteFailed(false); }} autoFocus disabled={deleting}>Keep it</button>
              <button className="bo-btn bo-btn--secondary" onClick={() => void removeRecording()} disabled={deleting}>Delete recording</button>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

function subtitle(f: Fix): string {
  if (f.kind === "sound") return `"${f.saidHighlight}"`;
  if (f.kind === "upgrade") return "Your sentence, one step better";
  return f.word ? `"${f.word}" × ${f.count}` : "Point, reason, example";
}

function Item({ n, title, sub, on, onClick }: { n: React.ReactNode; title: string; sub: string; on: boolean; onClick: () => void }) {
  return (
    <div className="dk-item" aria-current={on ? "true" : undefined} onClick={onClick} role="button" tabIndex={0}
      onKeyDown={(e) => { if (e.key === "Enter") onClick(); }}>
      <span className="dk-item__n">{n}</span>
      <div><div className="dk-item__t">{title}</div><div className="dk-item__s">{sub}</div></div>
    </div>
  );
}

function FixCard({ fix, n, of, disputing, deviceId, onDispute, playRef }: { fix: Fix; n: number; of: number; disputing: boolean;
  deviceId: string | null; onDispute: () => void; playRef: (p: () => void) => void }) {
  const kindLabel = fix.kind === "sound" ? "Sound fix" : fix.kind === "upgrade" ? "Say it better" : fix.title;
  const disputeButton = <button className="bo-btn bo-btn--quiet-neutral" disabled={disputing} onClick={onDispute}>That&apos;s not what I said</button>;
  return (
    <div className="dk-detail on">
      <article className="bo-fix" style={{ padding: 28 }}>
        <div className="bo-fix__head"><span className="bo-eyebrow bo-eyebrow--coach">{kindLabel} · {n} of {of}</span></div>
        {fix.kind === "sound" && (
          <>
            <h2 className="bo-fix__title">{fix.title} <span style={{ font: "500 15px/20px var(--font-sans)", color: "var(--ink-secondary)" }}>· {fix.example}</span></h2>
            <div className="dk-fixpair">
              <div><p className="bo-fix__label">You said</p><Clip src={fix.clip} label="Play what you said" playRef={playRef} />
                <p className="bo-fix__sentence">&quot;{highlight(fix.said ?? "", [fix.saidHighlight ?? ""], "bo-said")}&quot;</p></div>
              <div><p className="bo-fix__label">Try</p><Clip src={fix.model} label="Play the model" model />
                <p className="bo-fix__sentence">&quot;{highlight(fix.better ?? "", [fix.betterHighlight ?? ""], "bo-better")}&quot;</p></div>
            </div>
            <Tip text={fix.tip} />
          </>
        )}
        {fix.kind === "upgrade" && (
          <>
            <div><p className="bo-fix__label">You said</p><p className="bo-fix__sentence bo-fix__sentence--before" style={{ margin: 0 }}>&quot;{fix.said}&quot;</p>
              <Clip src={fix.clip} label="Play what you said" playRef={playRef} /></div>
            <div><p className="bo-fix__label">One step better</p>
              <p className="bo-fix__sentence" style={{ margin: 0 }}>&quot;{highlight(fix.better ?? "", fix.changed ?? [], "bo-better")}&quot;</p></div>
            <Clip src={fix.model} label="Play the better version" model />
          </>
        )}
        {fix.kind === "habit" && (
          <>
            {fix.word
              ? <div><span className="bo-big-count">&quot;{fix.word}&quot; × {fix.count}</span></div>
              : <div><p className="title-2" style={{ margin: 0 }}>Your answers averaged {fix.averageWords} words.</p>
                  {fix.better && <p className="bo-fix__sentence">&quot;{fix.better}&quot;</p>}<Clip src={fix.model} label="Play an example" model /></div>}
            <Tip text={fix.tip} />
          </>
        )}
        {(fix.kind === "sound" || fix.kind === "upgrade") && fix.better && (
          <div style={{ marginTop: 18 }}><TryIt reference={fix.better} deviceId={deviceId} kind={fix.kind} /></div>
        )}
        <div style={{ display: "flex", justifyContent: "center", marginTop: 18 }}>{disputeButton}</div>
      </article>
    </div>
  );
}

function Tip({ text }: { text?: string }) {
  if (!text) return null;
  return <div className="bo-tip"><Icon name="sentiment_satisfied" /><p className="bo-tip__text">{text}</p></div>;
}

function Skills({ debrief, onTalkAgain, onDone, onPractise, history = false }: { debrief: Debrief; onTalkAgain: () => void; onDone: () => void;
  onPractise?: (pattern?: string) => void; history?: boolean }) {
  const s = debrief.skills;
  const rows: [string, Level | null | undefined][] = [["Pronunciation", s?.pronunciation], ["Fluency", s?.fluency],
    ["Sentence building", s?.sentenceBuilding], ["Vocabulary", s?.vocabulary]];
  const l = debrief.latency;
  return (
    <div className="dk-detail on">
      <div className="bo-card" style={{ padding: 28 }}>
        <p className="title-2" style={{ margin: "0 0 4px" }}>{debrief.firstSession ? "This is your starting point" : "Your skills today"}</p>
        {debrief.firstSession && <p className="subhead" style={{ color: "var(--ink-secondary)", margin: "0 0 12px" }}>Arrows appear after your next talk.</p>}
        {rows.filter(([, level]) => level).map(([name, level]) => (
          <div className="bo-skill" key={name}>
            <span className="bo-skill__name">{name}</span><span className="bo-skill__level">{LEVEL_WORD[level!]}</span>
            <div className="bo-meter">{[1, 2, 3].map((i) => <i key={i} className={i <= LEVEL_BARS[level!] ? "on" : undefined} />)}</div>
          </div>
        ))}
        {l?.p50Ms != null && l.p90Ms != null && (
          <p className="bo-footnote" style={{ marginTop: 12 }}>Bolo answered in about {seconds(l.p50Ms)} s (slowest {seconds(l.p90Ms)} s).</p>
        )}
      </div>
      <div style={{ display: "flex", gap: 12, justifyContent: "center", marginTop: 24 }}>
        {history && onPractise ? (
          <>
            <button className="bo-btn bo-btn--primary" onClick={() => onPractise(firstSound(debrief))}><Icon name="record_voice_over" fill />Practise these (3 min)</button>
            <button className="bo-btn bo-btn--secondary" onClick={onDone}>Back</button>
          </>
        ) : debrief.firstSession && onPractise ? (
          <>
            {/* US-6.8: the first loop ends calmly: a short practice on what came up, or later. */}
            <button className="bo-btn bo-btn--primary" onClick={() => onPractise(firstSound(debrief))}><Icon name="record_voice_over" fill />Practise (3 min)</button>
            <button className="bo-btn bo-btn--secondary" onClick={onDone}>Later</button>
          </>
        ) : (
          <>
            <button className="bo-btn bo-btn--primary" onClick={onTalkAgain}><Icon name="mic" fill />Talk again</button>
            <button className="bo-btn bo-btn--secondary" onClick={onDone}>Done</button>
          </>
        )}
      </div>
    </div>
  );
}

/** The debrief's first sound fix, as a practice-deck key ("V_BH"), if it has one. */
function firstSound(d: Debrief): string | undefined {
  const fix = d.fixes?.find((f) => f.kind === "sound") as (Fix & { pattern?: string }) | undefined;
  return fix?.pattern;
}
