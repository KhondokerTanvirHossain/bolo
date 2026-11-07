package app.bolo.speech.adapter.out.fake;

import app.bolo.speech.application.port.in.PronunciationResult;
import app.bolo.speech.application.port.in.ScoredPhoneme;
import app.bolo.speech.application.port.in.ScoredWord;
import app.bolo.speech.application.port.out.ScriptedAssessmentPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** For tests and keyless dev: by default every reference word scores 95; tests can set the next result or fail. */
public class FakeScriptedAssessment implements ScriptedAssessmentPort {

    private volatile PronunciationResult next;
    private volatile RuntimeException failWith;

    public void next(PronunciationResult result) {
        this.next = result;
    }

    public void failWith(RuntimeException e) {
        this.failWith = e;
    }

    @Override
    public String vendor() {
        return "fake";
    }

    @Override
    public PronunciationResult assess(byte[] wav16k, String reference) {
        if (failWith != null) throw failWith;
        if (next != null) return next;
        List<ScoredWord> words = new ArrayList<>();
        long t = 0;
        for (String w : reference.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").trim().split("\\s+")) {
            words.add(new ScoredWord(w, t, t + 300, 95, "None", List.of(new ScoredPhoneme(w.substring(0, 1), 95))));
            t += 350;
        }
        return new PronunciationResult(true, 95, words);
    }
}
