package app.bolo.speech.application.port.in;

import java.util.List;

/** A recognised word with its pronunciation scores. startMs/endMs are stream time: audio ms written since the stream opened. */
public record ScoredWord(String word, long startMs, long endMs, double accuracy, String errorType, List<ScoredPhoneme> phonemes) {
}
