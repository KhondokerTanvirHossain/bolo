package app.bolo.home.application.service;

import app.bolo.coaching.application.port.in.LatestDebriefUseCase;
import app.bolo.common.id.LearnerId;
import app.bolo.home.application.port.in.HomeUseCase;
import app.bolo.home.domain.HomePlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeService implements HomeUseCase {

    private final LatestDebriefUseCase debriefs;

    @Override
    public HomeView home(LearnerId learner) {
        var last = debriefs.latestFor(learner)
                .map(l -> new HomePlan.LastDebrief(l.talkId(), l.status(), l.viewed(), l.soundPattern(), l.soundTitle()));
        Card card = HomePlan.card(last).map(c -> new Card(c.kind(), c.talkId(), c.pattern(), c.title())).orElse(null);
        return new HomeView("talk", card);
    }
}
