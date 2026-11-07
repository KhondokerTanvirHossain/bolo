/** Records a mic stream with MediaRecorder: Opus in WebM where possible, MP4 on Safari. */
export function pickMimeType(isSupported: (type: string) => boolean): string {
  for (const type of ["audio/webm;codecs=opus", "audio/mp4"]) if (isSupported(type)) return type;
  return "";
}

export type Recording = {
  /** Stops and resolves with the whole clip. */
  stop(): Promise<Blob>;
  /** Stops and throws the audio away (interrupted mid-recording, US-1.7). */
  cancel(): void;
};

export function startRecording(stream: MediaStream): Recording {
  const mimeType = pickMimeType((t) => typeof MediaRecorder !== "undefined" && MediaRecorder.isTypeSupported(t));
  const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
  const chunks: Blob[] = [];
  recorder.addEventListener("dataavailable", (e) => { if (e.data.size > 0) chunks.push(e.data); });
  recorder.start(250);
  return {
    stop: () =>
      new Promise<Blob>((resolve) => {
        const done = () => resolve(new Blob(chunks, { type: recorder.mimeType || mimeType }));
        // A recorder whose track ended (headset unplugged) has already stopped and fired "stop": don't wait for it.
        if (recorder.state === "inactive") { done(); return; }
        recorder.addEventListener("stop", done, { once: true });
        recorder.stop();
      }),
    cancel: () => {
      chunks.length = 0;
      if (recorder.state !== "inactive") recorder.stop();
    },
  };
}
