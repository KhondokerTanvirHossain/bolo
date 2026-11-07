"use client";
import { useEffect, useState } from "react";
import { Icon } from "@/scene/Scene";
import { Clip } from "./Clip";
import { fetchDebrief, pollDebrief, disputeFix, type Debrief, type Fix, type Level } from "./debrief";
import { highlight } from "./highlight";

type Props = { talkId: string; onTalkAgain: () => void; onDone: () => void; onNoDebrief?: () => void };

const LEVEL_WORD: Record<Level, string> = { BUILDING: "Building", GOOD: "Good", STRONG: "Strong" };
const LEVEL_BARS: Record<Level, number> = { BUILDING: 1, GOOD: 2, STRONG: 3 };

function seconds(ms: number): string {
  return (ms / 1000).toFixed(1);
}

/** After a talk: "Your coach is listening back", then the debrief (DBF-1..5), or the too-short / failed states. */
export function CoachFlow({ talkId, onTalkAgain, onDone, onNoDebrief }: Props) {
  const [debrief, setDebrief] = useState<Debrief | null>({ status: "scoring" });

  useEffect(() => {
    let cancelled = false;
    void pollDebrief({ fetch: () => fetchDebrief(talkId), now: () => Date.now(),
      sleep: (ms) => new Promise((r) => setTimeout(r, ms)) }, (d) => { if (!cancelled) setDebrief(d); }, () => cancelled)
      .then((d) => { if (!cancelled && d === null) setDebrief(null); });
    return () => { cancelled = true; };
  }, [talkId]);

  useEffect(() => { if (debrief === null) onNoDebrief?.(); }, [debrief, onNoDebrief]);

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
  return <DebriefScreen talkId={talkId} debrief={debrief} onTalkAgain={onTalkAgain} onDone={onDone} />;
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
      <div className="dk-talk" style={{ display: "flex", alignItems: "center", justifyContent: "center" }}>
        <div className="dk-card" role="status" style={{ position: "relative" }}>
          <h1 className="bo-flow__title">{title}</h1>
          <p className="bo-flow__body">{body}</p>
          <button className="bo-btn bo-btn--primary bo-btn--block" style={{ marginTop: 16 }} onClick={onPrimary} autoFocus>{primary}</button>
          <button className="bo-btn bo-btn--secondary bo-btn--block" style={{ marginTop: 10 }} onClick={onDone}>Done</button>
        </div>
      </div>
    </section>
  );
}

function DebriefScreen({ talkId, debrief, onTalkAgain, onDone }: { talkId: string; debrief: Debrief } & Omit<Props, "talkId">) {
  const [fixes, setFixes] = useState<Fix[]>(debrief.fixes ?? []);
  const [at, setAt] = useState(0);
  const [note, setNote] = useState<string | null>(null);
  const [playMine, setPlayMine] = useState<(() => void) | null>(null);
  const cards = 1 + fixes.length + 1;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowDown") { e.preventDefault(); setAt((a) => Math.min(cards - 1, a + 1)); setNote(null); }
      else if (e.key === "ArrowUp") { e.preventDefault(); setAt((a) => Math.max(0, a - 1)); setNote(null); }
      else if (e.key === " " && playMine) { e.preventDefault(); playMine(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [cards, playMine]);

  const dispute = async (index: number) => {
    if (!(await disputeFix(talkId, index))) return;
    setFixes((f) => f.filter((_, i) => i !== index));
    setNote("Thanks, we've removed it");
  };
  const register = (play: () => void) => { if (playMine !== play) setPlayMine(() => play); };

  return (
    <section className="dk-screen on" data-screen="debrief">
      <div className="dk-deb">
        <div className="dk-deb__list">
          <div className="bo-eyebrow bo-eyebrow--coach" style={{ padding: "0 14px 12px" }}><span className="bo-coach-mark">C</span>Your coach · today&apos;s talk</div>
          <Item n={<Icon name="star" fill size="sm" />} title="Something you did well" sub="Strength" on={at === 0} onClick={() => setAt(0)} />
          {fixes.map((f, i) => (
            <Item key={`${f.kind}-${i}-${f.title}`} n={String(i + 1)} title={f.title} sub={subtitle(f)} on={at === i + 1} onClick={() => setAt(i + 1)} />
          ))}
          <Item n={<Icon name="insights" size="sm" />} title={debrief.firstSession ? "Your starting point" : "Your skills today"}
            sub="Skills and what's next" on={at === cards - 1} onClick={() => setAt(cards - 1)} />
          <div style={{ marginTop: "auto", padding: "0 14px" }}>
            <button className="bo-btn bo-btn--quiet-neutral" style={{ padding: 0 }} onClick={onDone}><Icon name="close" size="sm" />Close feedback</button>
          </div>
        </div>
        <div className="dk-deb__pane">
          {note && <p className="bo-footnote" role="status" style={{ textAlign: "center", marginBottom: 12 }}>{note}</p>}
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
            <FixCard fix={fixes[at - 1]} n={at} of={fixes.length} onDispute={() => void dispute(at - 1)} playRef={register} />
          )}
          {at === cards - 1 && <Skills debrief={debrief} onTalkAgain={onTalkAgain} onDone={onDone} />}
          <div className="dk-deb__nav"><span><span className="dk-kbd">↑</span> <span className="dk-kbd">↓</span> move between cards</span><span><span className="dk-kbd">Space</span> plays your clip</span></div>
        </div>
      </div>
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

function FixCard({ fix, n, of, onDispute, playRef }: { fix: Fix; n: number; of: number; onDispute: () => void; playRef: (p: () => void) => void }) {
  const kindLabel = fix.kind === "sound" ? "Sound fix" : fix.kind === "upgrade" ? "Say it better" : fix.title;
  const disputeButton = <button className="bo-btn bo-btn--quiet-neutral" onClick={onDispute}>That&apos;s not what I said</button>;
  return (
    <div className="dk-detail on">
      <article className="bo-fix" style={{ padding: 28 }}>
        <div className="bo-fix__head"><span className="bo-eyebrow bo-eyebrow--coach">{kindLabel} · {n} of {of}</span></div>
        {fix.kind === "sound" && (
          <>
            <h2 className="bo-fix__title">{fix.title} <span style={{ font: "500 15px/20px var(--font-sans)", color: "var(--ink-secondary)" }}>· {fix.example}</span></h2>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20 }}>
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
        <div style={{ display: "flex", justifyContent: "center", marginTop: 18 }}>{disputeButton}</div>
      </article>
    </div>
  );
}

function Tip({ text }: { text?: string }) {
  if (!text) return null;
  return <div className="bo-tip"><Icon name="sentiment_satisfied" /><p className="bo-tip__text">{text}</p></div>;
}

function Skills({ debrief, onTalkAgain, onDone }: { debrief: Debrief; onTalkAgain: () => void; onDone: () => void }) {
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
        <button className="bo-btn bo-btn--primary" onClick={onTalkAgain}><Icon name="mic" fill />Talk again</button>
        <button className="bo-btn bo-btn--secondary" onClick={onDone}>Done</button>
      </div>
    </div>
  );
}
