package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

/** The English teacher hat: Bolo taught a sentence ({@code result} null) or judged the retry (analytics). */
public record TeacherMoment(TalkSessionId id, LearnerId learner, String result) {
}
