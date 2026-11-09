package app.bolo.coaching.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** DRL-1: up to 3 drills from a talk's fixes, one per fix, in fix order. */
class DrillPlanTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final DrillPlan.PairsLookup PAIRS = pattern -> "V_BH".equals(pattern)
            ? Optional.of(new DrillPlan.Pairs(List.<String[]>of(new String[]{"very", "berry", "very"}), List.of("very", "voice", "video", "seven", "live")))
            : Optional.empty();

    static JsonNode drills(String fixes) {
        return JSON.readTree(DrillPlan.drills(JSON.readTree("{\"status\":\"ready\",\"fixes\":" + fixes + "}"), PAIRS));
    }

    static final String SOUND = "{\"kind\":\"sound\",\"pattern\":\"V_BH\",\"title\":\"The v sound\",\"tip\":\"Top teeth on lip.\"}";
    static final String UP1 = "{\"kind\":\"upgrade\",\"title\":\"Say it better\",\"said\":\"I go market\",\"better\":\"I went to the market.\",\"changed\":[\"went\",\"to\",\"the\"]}";
    static final String UP2 = "{\"kind\":\"upgrade\",\"title\":\"Say it better\",\"said\":\"it was good\",\"better\":\"It was really good.\",\"changed\":[\"really\"]}";

    @Test
    void aSoundFixMakesPairs() {
        JsonNode d = drills("[" + SOUND + "]");
        assertThat(d.size()).isEqualTo(1);
        assertThat(d.get(0).path("kind").asString()).isEqualTo("pairs");
        assertThat(d.get(0).path("listen").get(0).path("target").asString()).isEqualTo("very");
        assertThat(d.get(0).path("say").size()).isEqualTo(5);
        assertThat(d.get(0).path("key").asString()).isEqualTo("pairs:V_BH");
    }

    @Test
    void upgradesMakeSayBetterThenShadow() {
        JsonNode d = drills("[" + UP1 + "," + UP2 + "]");
        assertThat(d.get(0).path("kind").asString()).isEqualTo("sayBetter");
        assertThat(d.get(0).path("better").asString()).isEqualTo("I went to the market.");
        assertThat(d.get(0).path("keyWords").size()).isEqualTo(3);
        assertThat(d.get(1).path("kind").asString()).isEqualTo("shadow");
        assertThat(d.get(1).path("sentence").asString()).isEqualTo("It was really good.");
    }

    @Test
    void shortAnswersMakeAShadow() {
        JsonNode d = drills("[{\"kind\":\"habit\",\"title\":\"Longer answers\",\"better\":\"I like it because it is quiet, for example at night.\"}]");
        assertThat(d.get(0).path("kind").asString()).isEqualTo("shadow");
    }

    @Test
    void fillerMakesNoDrill() {
        assertThat(drills("[{\"kind\":\"habit\",\"title\":\"Filler watch\",\"word\":\"um\",\"count\":5}]").size()).isZero();
        assertThat(drills("[]").size()).isZero();
    }

    @Test
    void atMostThree() {
        assertThat(drills("[" + SOUND + "," + UP1 + "," + UP2 + "," + UP1 + "]").size()).isEqualTo(3);
    }

    @Test
    void textsAreEveryWordAndSentenceTheDrillsSpeak() {
        JsonNode d = drills("[" + SOUND + "," + UP1 + "," + UP2 + "]");
        assertThat(DrillPlan.texts(d)).contains("very", "berry", "voice", "I went to the market.", "It was really good.");
    }
}
