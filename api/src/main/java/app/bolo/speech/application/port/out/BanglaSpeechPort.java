package app.bolo.speech.application.port.out;

import app.bolo.speech.application.port.in.HeardBangla;

import java.util.Optional;

/** A vendor's one-shot Bangla recognition of a short WAV (at most 60 s). Empty when nothing was heard. */
public interface BanglaSpeechPort {

    Optional<HeardBangla> recognise(byte[] wav16k, long timeoutMs);
}
