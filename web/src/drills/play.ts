/** Plays a clip; {@code rate} 0.75 is the slow (turtle) speed (US-5.3). Resolves when it ends (or can't play). */
export function play(url: string, rate = 1): Promise<void> {
  return new Promise((resolve) => {
    const a = new Audio(url);
    a.playbackRate = rate;
    a.onended = () => resolve();
    a.onerror = () => resolve();
    void a.play().catch(() => resolve());
  });
}
