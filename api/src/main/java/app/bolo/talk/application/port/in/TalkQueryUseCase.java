package app.bolo.talk.application.port.in;

import app.bolo.common.id.TalkSessionId;

import java.util.Optional;

public interface TalkQueryUseCase {

    Optional<TalkRecord> record(TalkSessionId id);
}
