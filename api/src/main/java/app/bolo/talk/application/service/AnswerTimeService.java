package app.bolo.talk.application.service;

import app.bolo.learner.application.port.in.AutoAnswerTimeUseCase;
import app.bolo.talk.application.port.in.TalkEnded;
import app.bolo.talk.application.port.out.TurnPort;
import app.bolo.talk.domain.AnswerSpeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** AX-7 (2026-11-14): after every talk, Bolo's automatic Time to answer is picked again from the latest answers. */
@Slf4j
@Service
@RequiredArgsConstructor
class AnswerTimeService {

    private final TurnPort turns;
    private final AutoAnswerTimeUseCase auto;

    @EventListener
    public void onTalkEnded(TalkEnded e) {
        try {
            AnswerSpeed.pick(turns.recentStartDelays(e.learner(), AnswerSpeed.RECENT)).ifPresent(speed -> auto.record(e.learner(), speed));
        } catch (RuntimeException ex) {
            log.warn("automatic time to answer not picked after talk {}: {}", e.id(), ex.getClass().getSimpleName());
        }
    }
}
