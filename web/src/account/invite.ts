/** Closed-beta invite links (?invite=CODE): kept for this browser session, sent when a newcomer starts. */
const KEY = "bolo.invite";
const CODE = /^[a-z0-9]{6,32}$/;

export function takeInviteFromUrl(): void {
  try {
    const url = new URL(window.location.href);
    const code = url.searchParams.get("invite");
    if (code === null) return;
    if (CODE.test(code)) window.sessionStorage.setItem(KEY, code);
    url.searchParams.delete("invite");
    window.history.replaceState(null, "", url.pathname + url.search + url.hash);
  } catch {
    // no invite then
  }
}

export function currentInvite(): string | null {
  try {
    return window.sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}
