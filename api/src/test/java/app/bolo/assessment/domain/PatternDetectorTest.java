package app.bolo.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static app.bolo.assessment.domain.Words.sound;
import static app.bolo.assessment.domain.Words.turn;
import static app.bolo.assessment.domain.Words.word;
import static org.assertj.core.api.Assertions.assertThat;

class PatternDetectorTest {

    @Test
    void aSoundMissedTwiceIsACandidate() {
        var turns = List.of(
                turn(1, "it is very good", word("very", 1000, 1400, 60, sound("v", 30), sound("ɛ", 90))),
                turn(3, "a vivid voice", word("voice", 2000, 2500, 65, sound("v", 40), sound("ɔɪ", 95))));
        List<SoundIssue> issues = PatternDetector.candidates(turns);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().pattern()).isEqualTo(SoundPattern.V_BH);
        assertThat(issues.getFirst().count()).isEqualTo(2);
        assertThat(issues.getFirst().examples().getFirst().word()).isEqualTo("very");
    }

    @Test
    void onceIsNotEnough() {
        var turns = List.of(turn(1, "very", word("very", 0, 400, 60, sound("v", 30))));
        assertThat(PatternDetector.candidates(turns)).isEmpty();
    }

    @Test
    void aClearWordIsNeverFlaggedForOneSoftSound() {
        var turns = List.of(
                turn(1, "very", word("very", 0, 400, 90, sound("v", 30))),
                turn(3, "very", word("very", 0, 400, 90, sound("v", 30))));
        assertThat(PatternDetector.candidates(turns)).isEmpty();
    }

    @Test
    void thCoversBothSounds() {
        var turns = List.of(
                turn(1, "think", word("think", 0, 400, 50, sound("θ", 20))),
                turn(3, "this", word("this", 0, 400, 50, sound("ð", 20))));
        assertThat(PatternDetector.candidates(turns).getFirst().pattern()).isEqualTo(SoundPattern.TH_TD);
    }
}
