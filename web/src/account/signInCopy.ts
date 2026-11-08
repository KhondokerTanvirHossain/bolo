import type { SignInResult } from "./session";

/** What to say when Continue with Google didn't finish; null when there is nothing to say (the learner closed it). */
export function signInProblem(r: SignInResult): string | null {
  switch (r.kind) {
    case "ok":
    case "cancelled":
      return null;
    case "blocked":
      return "Your browser blocked Google's sign-in window. Allow pop-ups for this site, then try again.";
    case "invite":
      return "Bolo is in a closed beta. Ask for an invite link, then try again.";
    case "off":
      return "Sign-in isn't available right now. Your progress stays in this browser.";
    default:
      return "Sign-in didn't work. Check your connection and try again.";
  }
}
