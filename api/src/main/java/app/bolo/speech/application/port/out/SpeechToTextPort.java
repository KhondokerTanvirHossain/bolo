package app.bolo.speech.application.port.out;

import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;

public interface SpeechToTextPort {

    String vendor();

    TranscriptionStream open(TranscriptListener listener);
}
