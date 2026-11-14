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
    private final app.bolo.talk.application.port.in.FreeTalksUseCase freeTalks;
    private final app.bolo.coaching.application.port.in.LoopDaysUseCase loops;
    private final app.bolo.assessment.application.port.in.PracticeDaysUseCase practice;
    private final app.bolo.common.time.BoloClock clock;
    private final app.bolo.learner.application.port.in.VoiceIntroUseCase intros;
    private final app.bolo.reminder.application.port.in.ReminderUseCase reminders;

    @Override
    public HomeView home(LearnerId learner) {
        var last = debriefs.latestFor(learner)
                .map(l -> new HomePlan.LastDebrief(l.talkId(), l.status(), l.viewed(), l.soundPattern(), l.soundTitle()));
        Integer introDue = intros.voice(learner).due();
        Card card = HomePlan.card(last, introDue, () -> reminders.account(learner) && reminders.cardDue(learner, clock.today())).map(c -> new Card(c.kind(), c.talkId(), c.pattern(), c.title(), c.day())).orElse(null);
        var activity = new java.util.ArrayList<java.time.Instant>(loops.loopDays(learner));
        activity.addAll(practice.practiceDays(learner));
        return new HomeView("talk", card, freeTalks.allowance(learner).orElse(null), loops.lastTopic(learner).orElse(null),
                app.bolo.home.domain.Streak.of(activity, clock.now()));
    }
}
