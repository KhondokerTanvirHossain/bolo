"use client";
import { useCallback, useEffect, useReducer, useRef } from "react";
import { api } from "@/api/browser";
import { classifyMicError } from "@/audio/micErrors";
import { errorName, openMic } from "@/audio/openMic";
import { createDownsampler, downsampleTo16k } from "./pcm";
import { createPlayer, type Player } from "./player";
import { initialTalk, talk, type ServerMessage } from "./talkState";

type Live = {
  ws: WebSocket | null;
  ctx: AudioContext;
  mic: MediaStream | null;
  node: AudioWorkletNode | null;
  player: Player;
  turnEndAt: number;
  heardTurn: number | null;
  pendingPlayed: number | null;
  ended: boolean;
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
export function useTalk(deviceId: string | null) {
  const [state, dispatch] = useReducer(talk, initialTalk);
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
      pendingPlayed: null, ended: false, endedByServer: false, endTimer: null };
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
      dispatch(created.kind === "refused" && created.code === "busy" ? { type: "busy" } : { type: "socket_closed" });
      return;
    }
    dispatch({ type: "started", minutes: created.value.minutes });

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
        l.player.enqueue(e.data);
        return;
      }
      let m: ServerMessage;
      try {
        m = JSON.parse(e.data) as ServerMessage;
      } catch {
        return;
      }
      if (m.type === "turn_end") {
        l.turnEndAt = performance.now();
        l.heardTurn = m.turn + 1;
        l.player.newTurn();
      } else if (m.type === "ended") {
        l.endedByServer = true;
      } else if (m.type === "buddy_done") {
        l.pendingPlayed = m.turn;
        maybePlayed();
      }
      dispatch(m);
    };
    ws.onclose = () => {
      dispatch({ type: "socket_closed" });
      if (live.current === l) teardown();
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
            for (const frame of downsampleTo16k(ev.data, down)) {
              if (ws.readyState === WebSocket.OPEN) ws.send(frame.buffer);
            }
          };
        } catch {
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

  return { state, start, done, end, reset };
}
