package app.bolo.goldenset;

import app.bolo.assessment.domain.SoundPattern;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTest {

    @Test
    void theReportNamesTheGateEachPatternAndEveryDisagreement() {
        var outcomes = List.of(AgreementTest.outcome("vans", "very few vans visit the village",
                Set.of(new GoldenSet.Label(0, SoundPattern.V_BH)),
                AgreementTest.word("very", 50, "v", 20), AgreementTest.plain("few"), AgreementTest.word("vans", 95, "v", 95),
                AgreementTest.plain("visit"), AgreementTest.plain("the"), AgreementTest.word("village", 60, "v", 40)));
        Map<Double, Agreement> sweep = new LinkedHashMap<>();
        for (double t : List.of(40.0, 50.0, 60.0, 70.0)) sweep.put(t, Agreement.of(outcomes, t, 75, 5));
        var set = new GoldenSet(Path.of("x"), "demo", "test", "ours", outcomes.stream().map(Agreement.Outcome::rec).toList());
        String md = Report.markdown(set, Agreement.of(outcomes, 60, 75, 5), sweep);
        assertThat(md).contains("Gate 1: NOT DECIDED");
        for (SoundPattern p : SoundPattern.values()) assertThat(md).contains(p.title());
        assertThat(md).contains("| vans | village |");
        assertThat(md).contains("| 40 |").contains("| 50 |").contains("| 60 |").contains("| 70 |");
        assertThat(md).contains("Measured on scripted reading");
    }
}
