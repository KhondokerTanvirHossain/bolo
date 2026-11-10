/** The clip playing now, so leaving the drills (Skip, Close) can stop it. */
let current: HTMLAudioElement | null = null;

/**
 * Plays a clip; {@code rate} 0.75 is the slow (turtle) speed (US-5.3). Resolves true when it played to the end, false when
 * it couldn't play (a 404, a limit, no network), so a drill can say so instead of going on in silence.
 */
export function play(url: string, rate = 1): Promise<boolean> {
  stopPlaying();
  return new Promise((resolve) => {
    const a = new Audio(url);
    current = a;
    a.playbackRate = rate;
    a.onended = () => resolve(true);
    a.onerror = () => resolve(false);
    void a.play().catch(() => resolve(false));
  });
}

export function stopPlaying() {
  current?.pause();
  current = null;
}
