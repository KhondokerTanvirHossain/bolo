package app.bolo.goldenset;

import app.bolo.assessment.domain.SoundPattern;
import app.bolo.common.audio.Wav;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenSetTest {

    static final Path DEMO = Path.of("src/test/resources/goldenset/demo");

    @TempDir Path dir;

    @Test
    void loadsTheDemoSet() {
        GoldenSet set = GoldenSet.load(DEMO);
        assertThat(set.name()).isEqualTo("demo");
        assertThat(set.recordings()).extracting(GoldenSet.Recording::id).containsExactly("vans", "school");
        assertThat(set.recordings().get(0).labels()).containsExactly(new GoldenSet.Label(0, SoundPattern.V_BH));
        assertThat(set.recordings().get(1).labels()).containsExactlyInAnyOrder(
                new GoldenSet.Label(5, SoundPattern.S_CLUSTER_VOWEL), new GoldenSet.Label(8, SoundPattern.S_CLUSTER_VOWEL));
    }

    /** The committed synthetic set (clear AI voices plus our two practice clips) stays loadable. */
    @Test
    void theSyntheticSetLoads() {
        GoldenSet set = GoldenSet.load(Path.of("../golden-sets/synthetic"));
        assertThat(set.recordings()).hasSize(14);
        assertThat(set.recordings().stream().filter(r -> !r.labels().isEmpty())).extracting(GoldenSet.Recording::id)
                .containsExactly("practice-ischool");
    }

    @Test
    void nthPicksTheOccurrence() throws Exception {
        write("Ask her to bring these things with her.", "{\"word\": \"her\", \"nth\": 2, \"pattern\": \"Z_J\"}");
        assertThat(GoldenSet.load(dir).recordings().get(0).labels()).containsExactly(new GoldenSet.Label(7, SoundPattern.Z_J));
    }

    @Test
    void anAmbiguousLabelIsAnError() throws Exception {
        write("Ask her to bring these things with her.", "{\"word\": \"her\", \"pattern\": \"Z_J\"}");
        assertThatThrownBy(() -> GoldenSet.load(dir)).isInstanceOf(GoldenSetException.class)
                .hasMessageContaining("r1").hasMessageContaining("nth");
    }

    @Test
    void aLabelOnAWordNotInTheReferenceIsAnError() throws Exception {
        write("Very few vans.", "{\"word\": \"village\", \"pattern\": \"V_BH\"}");
        assertThatThrownBy(() -> GoldenSet.load(dir)).isInstanceOf(GoldenSetException.class)
                .hasMessageContaining("r1").hasMessageContaining("village");
    }

    @Test
    void anUnknownPatternIsAnError() throws Exception {
        write("Very few vans.", "{\"word\": \"very\", \"pattern\": \"R_L\"}");
        assertThatThrownBy(() -> GoldenSet.load(dir)).isInstanceOf(GoldenSetException.class).hasMessageContaining("R_L");
    }

    @Test
    void aDuplicateIdIsAnError() throws Exception {
        audio("a.wav", 16000, 1);
        Files.writeString(dir.resolve("manifest.json"), """
                {"name":"x","source":"x","licence":"x","recordings":[
                 {"id":"r1","audio":"a.wav","reference":"Very.","labelledBy":"t","issues":[]},
                 {"id":"r1","audio":"a.wav","reference":"Very.","labelledBy":"t","issues":[]}]}""");
        assertThatThrownBy(() -> GoldenSet.load(dir)).isInstanceOf(GoldenSetException.class).hasMessageContaining("r1");
    }

    @Test
    void audioMustBe16kMonoAndAtMost60s() throws Exception {
        write("Very few vans.", "");
        audio("a.wav", 8000, 1);
        assertThatThrownBy(() -> GoldenSet.load(dir)).isInstanceOf(GoldenSetException.class)
                .hasMessageContaining("a.wav").hasMessageContaining("16 kHz");
        audio("a.wav", 16000, 61);
        assertThatThrownBy(() -> GoldenSet.load(dir)).isInstanceOf(GoldenSetException.class)
                .hasMessageContaining("a.wav").hasMessageContaining("60 s");
        Files.delete(dir.resolve("a.wav"));
        assertThatThrownBy(() -> GoldenSet.load(dir)).isInstanceOf(GoldenSetException.class).hasMessageContaining("a.wav");
    }

    void write(String reference, String issue) throws Exception {
        if (!Files.exists(dir.resolve("a.wav"))) audio("a.wav", 16000, 1);
        Files.writeString(dir.resolve("manifest.json"), """
                {"name":"x","source":"x","licence":"x","recordings":[
                 {"id":"r1","audio":"a.wav","reference":"%s","labelledBy":"t","issues":[%s]}]}""".formatted(reference, issue));
    }

    void audio(String name, int rate, int seconds) throws Exception {
        Files.write(dir.resolve(name), Wav.wrap(new byte[rate * 2 * seconds], rate));
    }
}
