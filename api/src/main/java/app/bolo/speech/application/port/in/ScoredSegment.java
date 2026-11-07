package app.bolo.speech.application.port.in;

import java.util.List;

/** One finished stretch of recognised speech with word-level pronunciation scores (unscripted assessment). */
public record ScoredSegment(String text, List<ScoredWord> words) {
}
