"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Icon } from "@/scene/Scene";
import { Attempts } from "./Attempts";
import { better, sayUrl, TRIES, type Level, type PairsDrill as Pairs } from "./drills";
import { play } from "./play";

/**
 * Minimal pairs (DRL-2, US-5.2): 3 listening rounds (hear one word, pick it; a miss plays both slowly), then 5 words to
 * say. Keys 1 and 2 pick; L listens again.
 */
export function PairsDrill({ drill, talkId, deviceId, onFinish }: { drill: Pairs; talkId: string; deviceId: string | null;
  onFinish: (first?: Level, best?: Level) => void }) {
  const [round, setRound] = useState(0);
  const [tries, setTries] = useState(0);
  const [answer, setAnswer] = useState<"right" | "again" | "giveup" | null>(null);
  const [silent, setSilent] = useState(false);
  const [word, setWord] = useState(0);
  const [first, setFirst] = useState<Level | undefined>();
  const [best, setBest] = useState<Level | undefined>();
  const listening = round < drill.listen.length;
  const r = listening ? drill.listen[round] : null;
  // The word played this round: either of the pair, so the learner really listens.
  const played = useMemo(() => (r ? (Math.random() < 0.5 ? r.a : r.b) : ""), [r]);

  const listen = useCallback(() => {
    if (played) void play(sayUrl(talkId, played)).then((ok) => setSilent(!ok));
  }, [played, talkId]);
  useEffect(() => { if (listening) listen(); }, [listening, listen]);

  const next = () => { setAnswer(null); setTries(0); setRound((n) => n + 1); };
  const pick = (w: string) => {
    if (!r || answer === "right" || answer === "giveup") return;
    if (w === played) { setAnswer("right"); setTimeout(next, 800); return; }
    const t = tries + 1;
    setTries(t);
    if (t >= TRIES) { setAnswer("giveup"); setTimeout(next, 1500); return; }
    setAnswer("again");
    void (async () => { await play(sayUrl(talkId, r.a), 0.75); await play(sayUrl(talkId, r.b), 0.75); })();
  };

  useEffect(() => {
    if (!listening) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.repeat) return;
      if (e.key === "1" && r) pick(r.a);
      else if (e.key === "2" && r) pick(r.b);
      else if (e.key === "l" || e.key === "L") listen();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  if (listening && r) {
    return (
      <div className="dk-drill" data-drill="pairs">
        <p className="bo-eyebrow bo-eyebrow--coach"><span className="bo-coach-mark">C</span>{drill.title} · Hear the difference</p>
        <h2 className="title-1" style={{ margin: "8px 0 16px" }}>Which did you hear?</h2>
        <button className="bo-talk dk-drill__listen" aria-label="Listen again" onClick={listen}><Icon name="volume_up" fill /></button>
        <div className="dk-pairs">
          {[r.a, r.b].map((w, i) => (
            <button key={w} className={`bo-pair${answer === "right" && w === played ? " bo-pair--right" : ""}`} onClick={() => pick(w)}>
              <span className="bo-pair__word">{w}</span><span className="dk-kbd" aria-hidden="true">{i + 1}</span>
            </button>
          ))}
        </div>
        <p className="bo-footnote" role="status" style={{ minHeight: 20 }}>
          {answer === null && silent ? "Couldn't play that. Check your connection, then press L." : answer === "right" ? "That's it." : answer === "again" ? `Listen again: ${r.a}, ${r.b}.` : answer === "giveup" ? "Good effort. We'll come back to this one." : ""}
        </p>
        <p className="bo-footnote">Listening round {round + 1} of {drill.listen.length}, then you say {drill.say.length} {drill.say.length === 1 ? "word" : "words"}. Press <span className="dk-kbd">L</span> to listen again.</p>
      </div>
    );
  }

  const w = drill.say[word];
  return (
    <div className="dk-drill" data-drill="pairs-say">
      <p className="bo-eyebrow bo-eyebrow--coach"><span className="bo-coach-mark">C</span>{drill.title} · Say it</p>
      <p className="dk-drill__word">{w}</p>
      <button className="bo-btn bo-btn--quiet-neutral" onClick={() => void play(sayUrl(talkId, w))}><Icon name="volume_up" />Hear it</button>
      <p className="bo-tip__text" style={{ margin: "4px 0 12px" }}>{drill.tip}</p>
      <Attempts key={w} reference={w} deviceId={deviceId} label="Try it"
        onTry={(level) => { if (level !== "NAILED") void play(sayUrl(talkId, w)); }} onDone={(f, b) => {
        const nf = first ?? f;
        const nb = better(best, b);
        setFirst(nf);
        setBest(nb);
        if (word + 1 < drill.say.length) setWord(word + 1); else onFinish(nf, nb);
      }} />
      <p className="bo-footnote">Word {word + 1} of {drill.say.length}</p>
    </div>
  );
}
