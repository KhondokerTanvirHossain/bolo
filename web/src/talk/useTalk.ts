"use client";
import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { levelOf } from "./level";
import { firstWordSpoken, track } from "@/analytics/track";
import { keepSlower } from "@/settings/settings";
import { api } from "@/api/browser";
import { classifyMicError } from "@/audio/micErrors";
import { errorName, openMic } from "@/audio/openMic";
import { createDownsampler, downsampleTo16k } from "./pcm";
import { createPlayer, type Player } from "./player";
import { initialTalk, talk, type ServerMessage } from "./talkState";

type Live = {
  ws: WebSocket | null;
  /** Diagnosis: one playback error report per talk. */
  playErrorSent?: boolean;
  ctx: AudioContext;
  mic: MediaStream | null;
  node: AudioWorkletNode | null;
  player: Player;
  turnEndAt: number;
  heardTurn: number | null;
  pendingPlayed: number | null;
  ended: boolean;
  paused: boolean;
  crisis: boolean;
  endedByServer: boolean;
  endTimer: ReturnType<typeof setTimeout> | null;
};

/** How long to wait for Bolo to confirm End before tearing down locally (a dead connection never answers). */
const END_GRACE_MS = 3000;

function socketUrl(id: string): string {
  const base = process.env.NEXT_PUBLIC_BOLO_WS
    ?? `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}`;
  return `${base}/ws/talk/${id}`;
}

/**
 * One talk with Bolo (TLK-1). The mic is opened before the talk is created, so a blocked mic never holds a live
 * slot. Mic audio goes up as 20 ms frames; Bolo's voice comes down as PCM and plays gap-free. The browser tells the
 * relay when Bolo's line has finished playing ("played"), and how long after the end of the learner's turn Bolo's
 * voice started ("heard", NFR-1).
 */
const LEVEL_EVERY_MS = 50;

/** Diagnosis (2026-11-14): why the browser stopped a talk, as a short key (no text, PRD §10). */
export function errorKey(e: unknown): string {
  const name = e instanceof Error || (typeof e === "object" && e !== null && "name" in e) ? String((e as { name: unknown }).name) : "unknown";
  return name.toLowerCase().replace(/[^a-z0-9_-]/g, "").slice(0, 40) || "unknown";
}

export function useTalk(deviceId: string | null) {
  const [state, dispatch] = useReducer(talk, initialTalk);
  /** P3a: the mic level (0–1) for the meter, published at most every LEVEL_EVERY_MS. */
  const [level, setLevel] = useState(0);
  const levelAt = useRef(0);
  const live = useRef<Live | null>(null);

  const teardown = useCallback(() => {
    const l = live.current;
    if (!l) return;
    live.current = null;
    l.ended = true;
    if (l.endTimer) clearTimeout(l.endTimer);
    l.node?.port.close();
    l.node?.disconnect();
    l.mic?.getTracks().forEach((t) => t.stop());
    l.player.stop();
    if (l.ws && l.ws.readyState <= WebSocket.OPEN) l.ws.close();
    void l.ctx.close().catch(() => {});
  }, []);

  useEffect(() => teardown, [teardown]);

  const sendJson = (l: Live, m: object) => {
    if (l.ws?.readyState === WebSocket.OPEN) l.ws.send(JSON.stringify(m));
  };

  const start = useCallback(async () => {
    teardown();
    dispatch({ type: "connecting" });
    // Created inside the click, so the browser lets it play sound.
    const ctx = new AudioContext();
    void ctx.resume().catch(() => {});
    const l: Live = { ws: null, ctx, mic: null, node: null, player: createPlayer(ctx), turnEndAt: 0, heardTurn: null,
      pendingPlayed: null, ended: false, paused: false, crisis: false, endedByServer: false, endTimer: null };
    live.current = l;

    try {
      const { stream } = await openMic(deviceId);
      // Ended (End tapped, unmounted) while the browser was still opening the mic: release it right away.
      if (l.ended) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      l.mic = stream;
    } catch (e) {
      teardown();
      dispatch({ type: "no_mic", problem: classifyMicError(errorName(e)) });
      return;
    }
    if (l.ended) return;

    const created = await api.post<{ id: string; minutes: number }>("/api/talk");
    if (l.ended) return;
    if (created.kind !== "ok") {
      teardown();
      dispatch(created.kind === "refused" && created.code === "busy" ? { type: "busy" }
        : created.kind === "refused" && created.code === "sign-in" ? { type: "needs_sign_in" }
        : created.kind === "refused" && created.code === "free-limit" ? { type: "free_limit" }
        : created.kind === "refused" && created.code === "daily-cap" ? { type: "daily_cap" } : { type: "socket_closed" });
      return;
    }
    dispatch({ type: "started", minutes: created.value.minutes, talkId: created.value.id });

    const maybePlayed = () => {
      if (l.pendingPlayed === null || !l.player.idle()) return;
      sendJson(l, { type: "played", turn: l.pendingPlayed });
      l.pendingPlayed = null;
    };
    l.player.onDrained(maybePlayed);
    l.player.onStart((delayMs) => {
      if (l.heardTurn === null) return;
      sendJson(l, { type: "heard", turn: l.heardTurn, ms: Math.round(performance.now() - l.turnEndAt + delayMs) });
      l.heardTurn = null;
    });

    const ws = new WebSocket(socketUrl(created.value.id));
    ws.binaryType = "arraybuffer";
    l.ws = ws;
    ws.onmessage = (e: MessageEvent<string | ArrayBuffer>) => {
      if (typeof e.data !== "string") {
        if (!l.paused) {
          try {
            l.player.enqueue(e.data);
          } catch (err) {
            if (!l.playErrorSent) {
              l.playErrorSent = true;
              void track("talk_client_error", { stage: "play", error: errorKey(err), bytes: e.data.byteLength });
            }
          }
        }
        return;
      }
      let m: ServerMessage;
      try {
        m = JSON.parse(e.data) as ServerMessage;
      } catch {
        return;
      }
      if (m.type === "turn_end") {
        // R4: the first spoken answer, if Day 1 didn't already count (once per browser).
        void firstWordSpoken();
        l.turnEndAt = performance.now();
        l.heardTurn = m.turn + 1;
        l.player.newTurn();
      } else if (m.type === "crisis") {
        // Bolo's ordinary reply stops at once; only the crisis script plays from here.
        l.crisis = true;
        l.player.stop();
      } else if (m.type === "ended") {
        l.endedByServer = true;
      } else if (m.type === "buddy_done") {
        l.pendingPlayed = m.turn;
        maybePlayed();
      }
      // P3a: the countdown runs from when the server's time left arrived.
      dispatch("leftMs" in m ? { ...m, at: Date.now() } : m);
    };
    ws.onclose = (ev: CloseEvent) => {
      if (!l.endedByServer && !l.ended) void track("talk_client_error", { stage: "socket_closed", code: ev.code, by_server: false });
      dispatch({ type: "socket_closed" });
      if (live.current !== l) return;
      // The server ends a crisis talk once the script is sent; let the learner hear all of it before closing audio.
      if (l.crisis && !l.player.idle()) {
        const done = () => { if (live.current === l) teardown(); };
        l.player.onDrained(done);
        setTimeout(done, 30_000);
        return;
      }
      teardown();
    };
    ws.onopen = () => {
      void (async () => {
        try {
          await ctx.audioWorklet.addModule("/worklets/pcm-capture.js");
          if (l.ended || !l.mic) return;
          const source = ctx.createMediaStreamSource(l.mic);
          const node = new AudioWorkletNode(ctx, "pcm-capture");
          const mute = ctx.createGain();
          mute.gain.value = 0;
          source.connect(node);
          node.connect(mute);
          mute.connect(ctx.destination);
          l.node = node;
          const down = createDownsampler(ctx.sampleRate);
          node.port.onmessage = (ev: MessageEvent<Float32Array>) => {
            if (l.paused) return;
            const now = performance.now();
            if (now - levelAt.current >= LEVEL_EVERY_MS) {
              levelAt.current = now;
              // Only a visible change re-renders the screen (quiet rooms and steady voices cost nothing).
              const next = Math.round(levelOf(ev.data) * 20) / 20;
              setLevel((prev) => (prev === next ? prev : next));
            }
            for (const frame of downsampleTo16k(ev.data, down)) {
              if (ws.readyState === WebSocket.OPEN) ws.send(frame.buffer);
            }
          };
        } catch (err) {
          void track("talk_client_error", { stage: "audio_setup", error: errorKey(err) });
          ws.close();
        }
      })();
    };
  }, [deviceId, teardown]);

  const done = useCallback(() => { if (live.current) sendJson(live.current, { type: "done" }); }, []);
  const end = useCallback(() => {
    const l = live.current;
    if (l?.ws?.readyState === WebSocket.OPEN) {
      sendJson(l, { type: "end" });
      l.endTimer = setTimeout(() => {
        if (live.current !== l || l.endedByServer) return;
        teardown();
        dispatch({ type: "reset" });
      }, END_GRACE_MS);
    } else {
      teardown();
      dispatch({ type: "reset" });
    }
  }, [teardown]);
  const reset = useCallback(() => { teardown(); dispatch({ type: "reset" }); }, [teardown]);

  /** TLK-10: the mic stops sending and Bolo's voice stops; the server keeps the talk for 30 minutes. */
  const pause = useCallback(() => {
    const l = live.current;
    if (!l || l.paused) return;
    l.paused = true;
    l.pendingPlayed = null;
    l.player.stop();
    sendJson(l, { type: "pause" });
  }, []);
  /** From a click, so the browser lets audio play again. */
  const resume = useCallback(() => {
    const l = live.current;
    if (!l) return;
    l.paused = false;
    void l.ctx.resume().catch(() => {});
    sendJson(l, { type: "resume" });
  }, []);
  const repeat = useCallback(() => { if (live.current) sendJson(live.current, { type: "repeat" }); }, []);

  // Leaving the tab pauses the talk (US-2.10); coming back shows "Paused. Tap to continue."
  useEffect(() => {
    const onVisibility = () => {
      const live = ["buddy_speaking", "your_turn", "thinking"];
      if (document.visibilityState === "hidden" && live.includes(state.phase)) pause();
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [state.phase, pause]);

  /** US-2.7: keep the slower Bolo for next time (Settings), or not; either way the question goes away. */
  const answerKeepSlower = useCallback((keep: boolean) => {
    dispatch({ type: "keep_slower_answered" });
    if (keep) void keepSlower();
  }, []);

  return { state, level, start, done, end, reset, pause, resume, repeat, answerKeepSlower };
}
