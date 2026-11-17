/** Founder 2026-11-18: "bn" when at least half the words carry Bangla letters, so the Bangla font and lang apply. */
export function langOf(text: string | null | undefined): "bn" | undefined {
  const words = (text ?? "").trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return undefined;
  const bangla = words.filter((w) => /[ঀ-৿]/.test(w)).length;
  return bangla * 2 >= words.length ? "bn" : undefined;
}
