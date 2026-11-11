package app.bolo.help.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Report a problem (P2b): a stuck learner's note with its context, for the admin's Help inbox. */
public interface HelpUseCase {

    /** email is null for a guest. */
    record Request(long id, String email, String message, Map<String, String> context, Instant at) {
    }

    /** Refuses "message" (empty or over 1000 characters); throws TooManyRequestsException past 5 an hour. */
    void report(LearnerId learner, String message, Map<String, String> context);

    List<Request> open();

    void done(long id);
}
