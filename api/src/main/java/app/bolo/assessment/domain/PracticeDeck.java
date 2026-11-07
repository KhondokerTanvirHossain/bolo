package app.bolo.assessment.domain;

import java.util.List;
import java.util.Map;

/** Practice sentences that put each pattern's sound in everyday words (3 each; reviewed by the teacher later). */
public final class PracticeDeck {

    private static final Map<SoundPattern, List<String>> DECK = Map.of(
            SoundPattern.V_BH, List.of("It is very important to visit the village.", "I have a van and a vest.",
                    "We moved to a very lively avenue."),
            SoundPattern.W_V, List.of("We will walk to work on Wednesday.", "What was the weather like in winter?",
                    "I want warm water, please."),
            SoundPattern.TH_TD, List.of("I think three of them are there.", "Thank you for the thoughtful gift.",
                    "This is the third thing I thought about."),
            SoundPattern.Z_J, List.of("The zoo is closed on Tuesdays.", "Please choose the size you need.",
                    "Zara was busy with easy puzzles."),
            SoundPattern.S_SH, List.of("She sells fresh fish in the shop.", "I wish I could finish my shift.",
                    "Show me the shop near the station."),
            SoundPattern.S_CLUSTER_VOWEL, List.of("I study at a small school near the station.", "Speak slowly and stay calm.",
                    "The street was full of students."));

    private PracticeDeck() {
    }

    public static List<String> sentences(SoundPattern pattern) {
        return DECK.get(pattern);
    }
}
