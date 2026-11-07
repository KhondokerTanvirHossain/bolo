package app.bolo.talk.application.port.out;

import app.bolo.common.id.TalkSessionId;

public interface TurnPort {

    /** speaker is "buddy" or "learner"; serverMs only for Buddy replies. */
    void save(TalkSessionId session, int idx, String speaker, String text, Integer serverMs);

    void heard(TalkSessionId session, int idx, int ms);
}
