package app.bolo.assessment.application.service;

import app.bolo.assessment.application.port.in.AssessTalkUseCase;
import app.bolo.assessment.domain.DebriefPlan;
import app.bolo.assessment.domain.FixChoice;
import app.bolo.assessment.domain.FluencyStats;
import app.bolo.assessment.domain.Habit;
import app.bolo.assessment.domain.PatternDetector;
import app.bolo.assessment.domain.SkillLevels;
import app.bolo.assessment.domain.SoundIssue;
import app.bolo.assessment.domain.SpokenSound;
import app.bolo.assessment.domain.SpokenWord;
import app.bolo.assessment.domain.TurnScore;
import app.bolo.assessment.domain.WordAt;
import app.bolo.speech.application.port.in.ScoredWord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AssessmentService implements AssessTalkUseCase {

    @Override
    public TalkAssessment assess(List<TurnInput> turns) {
        List<TurnScore> scores = turns.stream().map(t -> new TurnScore(t.idx(), t.text(), spoken(t.words()))).toList();
        FluencyStats stats = FluencyStats.of(scores);
        List<SoundCandidate> sounds = new ArrayList<>();
        for (SoundIssue issue : PatternDetector.candidates(scores)) {
            WordAt at = issue.examples().getFirst();
            sounds.add(new SoundCandidate(issue.pattern().name(), issue.pattern().title(), issue.pattern().example(), issue.pattern().tip(),
                    issue.count(), at.turnIdx(), at.word(), at.startMs(), at.endMs()));
        }
        Optional<HabitView> habit = Habit.of(stats).map(h -> new HabitView(h.kind().name(), h.word(), h.count(), h.averageWords()));
        double[] accuracies = scores.stream().flatMap(t -> t.words().stream()).filter(w -> !w.phonemes().isEmpty())
                .mapToDouble(SpokenWord::accuracy).toArray();
        String pronunciation = accuracies.length == 0 ? null
                : SkillLevels.pronunciation(java.util.Arrays.stream(accuracies).average().orElse(0)).name();
        return new TalkAssessment(stats.speechMs(), List.copyOf(sounds), habit, pronunciation,
                SkillLevels.fluency(stats.wordsPerMinute(), stats.longPausesPerMinute()).name());
    }

    @Override
    public List<FixSlot> plan(TalkAssessment a, int upgrades) {
        // The domain plan only needs to know how many sound issues and whether a habit exists.
        List<SoundIssue> sounds = a.sounds().stream().map(s -> new SoundIssue(null, List.of())).toList();
        Optional<Habit> habit = a.habit().map(h -> new Habit(Habit.Kind.valueOf(h.kind()), h.word(), h.count(), h.averageWords()));
        List<FixSlot> slots = new ArrayList<>();
        int soundIndex = 0;
        for (FixChoice c : DebriefPlan.choose(sounds, upgrades, habit)) {
            switch (c) {
                case FixChoice.Sound s -> slots.add(new FixSlot("sound", soundIndex++));
                case FixChoice.Upgrade u -> slots.add(new FixSlot("upgrade", u.index()));
                case FixChoice.HabitFix h -> slots.add(new FixSlot("habit", 0));
            }
        }
        return List.copyOf(slots);
    }

    private static List<SpokenWord> spoken(List<ScoredWord> words) {
        return words.stream().map(w -> new SpokenWord(w.word(), w.startMs(), w.endMs(), w.accuracy(), w.errorType(),
                w.phonemes().stream().map(p -> new SpokenSound(p.symbol(), p.accuracy())).toList())).toList();
    }
}
