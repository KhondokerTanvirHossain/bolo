/** Opens the mic with Bolo's capture settings. If a remembered device has gone (unplugged headset), it falls back to
 *  the default mic instead of trapping the learner, and says so via {@code deviceGone}. */
export async function openMic(deviceId: string | null): Promise<{ stream: MediaStream; deviceGone: boolean }> {
  const constraints = (id: string | null): MediaStreamConstraints => ({
    audio: {
      deviceId: id ? { exact: id } : undefined,
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    },
  });
  try {
    return { stream: await navigator.mediaDevices.getUserMedia(constraints(deviceId)), deviceGone: false };
  } catch (e) {
    const name = errorName(e);
    if (deviceId && (name === "OverconstrainedError" || name === "NotFoundError")) {
      return { stream: await navigator.mediaDevices.getUserMedia(constraints(null)), deviceGone: true };
    }
    throw e;
  }
}

/** Firefox's OverconstrainedError is not a DOMException, so read the name off any error-like value. */
export function errorName(e: unknown): string {
  return typeof e === "object" && e !== null && "name" in e && typeof e.name === "string" ? e.name : "";
}
