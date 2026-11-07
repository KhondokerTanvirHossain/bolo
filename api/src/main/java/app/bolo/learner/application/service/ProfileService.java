package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.UpdateProfileUseCase;
import app.bolo.learner.application.port.out.ProfilePort;
import app.bolo.learner.domain.FearedMoment;
import app.bolo.learner.domain.Goal;
import app.bolo.learner.domain.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService implements UpdateProfileUseCase {

    private final ProfilePort profiles;
    private final BoloClock clock;

    @Override
    public void update(LearnerId learner, String goal, String fearedMoment, int talkMinutes) {
        Goal g = Goal.fromKey(goal).orElseThrow(() -> new RefusedException("goal"));
        FearedMoment f = FearedMoment.fromKey(fearedMoment).orElseThrow(() -> new RefusedException("feared-moment"));
        if (talkMinutes != 5 && talkMinutes != 10) throw new RefusedException("talk-minutes");
        profiles.upsert(learner, new Profile(g, f, talkMinutes), clock.now());
    }
}
