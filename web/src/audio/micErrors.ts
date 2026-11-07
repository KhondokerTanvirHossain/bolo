/** Maps getUserMedia failures to what the learner can do about them (US-1.3). */
export type MicProblem = "blocked" | "busy" | "no-device";

export function classifyMicError(name: string): MicProblem {
  switch (name) {
    case "NotReadableError": // the device is held by another app (Zoom, Teams) or the OS
    case "AbortError":
      return "busy";
    case "NotFoundError":
    case "OverconstrainedError":
      return "no-device";
    default:
      return "blocked";
  }
}

/** Where to unblock the mic, in the learner's own browser. */
export function unblockGuidance(userAgent: string): string {
  if (/Edg\//.test(userAgent)) {
    return "Click the lock icon left of the address bar, open Permissions for this site, and set Microphone to Allow.";
  }
  if (/Firefox\//.test(userAgent)) {
    return "Click the crossed-out microphone in the address bar, then remove the block.";
  }
  if (/Chrome\//.test(userAgent)) {
    return "Click the icon left of the address bar, open Site settings, and set Microphone to Allow.";
  }
  if (/Safari\//.test(userAgent)) {
    return "In the menu bar choose Safari → Settings for this website, and set Microphone to Allow.";
  }
  return "Allow the microphone for this site in your browser's site settings.";
}
