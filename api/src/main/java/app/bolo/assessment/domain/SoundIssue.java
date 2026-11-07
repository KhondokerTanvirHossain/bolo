package app.bolo.assessment.domain;

import java.util.List;

public record SoundIssue(SoundPattern pattern, List<WordAt> examples) {

    public int count() {
        return examples.size();
    }
}
