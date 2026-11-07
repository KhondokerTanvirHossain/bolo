package app.bolo.coaching.adapter.in.lifecycle;

import app.bolo.coaching.application.service.CoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** A debrief that was scoring when the API stopped will never finish: show "couldn't prepare" instead of a spinner. */
@Component
@RequiredArgsConstructor
class FailStaleDebriefsOnStartup {

    private final CoachService coach;

    @EventListener(ApplicationReadyEvent.class)
    void failStale() {
        coach.failInterrupted();
    }
}
