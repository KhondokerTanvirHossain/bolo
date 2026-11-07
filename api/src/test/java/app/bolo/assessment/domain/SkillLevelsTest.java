package app.bolo.assessment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillLevelsTest {

    @Test
    void pronunciationFromMeanAccuracy() {
        assertThat(SkillLevels.pronunciation(90)).isEqualTo(Level.STRONG);
        assertThat(SkillLevels.pronunciation(75)).isEqualTo(Level.GOOD);
        assertThat(SkillLevels.pronunciation(60)).isEqualTo(Level.BUILDING);
    }

    @Test
    void fluencyFromPaceAndPauses() {
        assertThat(SkillLevels.fluency(120, 1.0)).isEqualTo(Level.STRONG);
        assertThat(SkillLevels.fluency(120, 4.0)).isEqualTo(Level.GOOD);
        assertThat(SkillLevels.fluency(90, 0)).isEqualTo(Level.GOOD);
        assertThat(SkillLevels.fluency(60, 0)).isEqualTo(Level.BUILDING);
    }
}
