package app.bolo.coaching.application.service;

import app.bolo.coaching.application.port.in.RatingUseCase;
import app.bolo.coaching.application.port.out.DebriefPort;
import app.bolo.coaching.application.port.out.RatingPort;
import app.bolo.common.error.RefusedException;
import app.bolo.common.event.LearnerMerged;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * DBF-8: asks after the feedback of the learner's 2nd, 3rd and 4th talks with feedback, then every third (7, 10,
 * 13 …), never after the first, and never twice for one talk.
 */
@Service
@RequiredArgsConstructor
public class RatingService implements RatingUseCase {

    static final int MAX_NOTE = 300;

    private final DebriefPort debriefs;
    private final RatingPort ratings;
    private final BoloClock clock;

    static boolean asksAt(int n) {
        return n == 2 || n == 3 || n == 4 || (n >= 7 && (n - 7) % 3 == 0);
    }

    @Override
    public Optional<Boolean> ask(LearnerId learner, TalkSessionId talk) {
        var d = debriefs.find(talk).filter(s -> learner.equals(s.learner()));
        if (d.isEmpty()) return Optional.empty();
        if (!"ready".equals(d.get().status()) || ratings.exists(talk)) return Optional.of(false);
        return Optional.of(asksAt(debriefs.readyCountBefore(learner, talk) + 1));
    }

    @Override
    public boolean save(LearnerId learner, TalkSessionId talk, Integer score, String note) {
        if (score != null && (score < 1 || score > 5)) throw new RefusedException("score");
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && (score == null || text.length() > MAX_NOTE)) throw new RefusedException("note");
        if (debriefs.find(talk).filter(s -> learner.equals(s.learner())).isEmpty()) return false;
        ratings.upsert(talk, learner, score, text, clock.now());
        return true;
    }

    @EventListener
    public void onLearnerMerged(LearnerMerged e) {
        ratings.move(e.from(), e.to());
    }
}
