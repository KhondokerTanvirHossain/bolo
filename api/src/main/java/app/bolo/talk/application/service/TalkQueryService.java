package app.bolo.talk.application.service;

import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.application.port.in.TalkQueryUseCase;
import app.bolo.talk.application.port.in.TalkRecord;
import app.bolo.talk.application.port.out.TurnPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TalkQueryService implements TalkQueryUseCase {

    private final TurnPort turns;
    private final app.bolo.talk.application.port.out.TalkSessionPort sessions;

    @Override
    public Optional<TalkRecord> record(TalkSessionId id) {
        return turns.record(id);
    }

    @Override
    public boolean startedSince(app.bolo.common.id.LearnerId learner, java.time.Instant since) {
        return sessions.startsBy(learner, since) > 0;
    }
}
