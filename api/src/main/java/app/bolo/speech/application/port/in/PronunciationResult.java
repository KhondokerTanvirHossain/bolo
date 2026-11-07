package app.bolo.speech.application.port.in;

import java.util.List;

/** A scripted assessment: was anything heard, the overall accuracy (0-100), and each word with miscues and sounds. */
public record PronunciationResult(boolean heard, double accuracy, List<ScoredWord> words) {

    public static PronunciationResult nothingHeard() {
        return new PronunciationResult(false, 0, List.of());
    }
}
