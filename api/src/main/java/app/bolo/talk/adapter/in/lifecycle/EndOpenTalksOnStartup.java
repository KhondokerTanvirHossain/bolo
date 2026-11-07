package app.bolo.talk.adapter.in.lifecycle;

import app.bolo.talk.application.port.in.StartTalkUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** One API instance owns every talk, so after a restart none is running: free their slots. */
@Component
@RequiredArgsConstructor
class EndOpenTalksOnStartup {

    private final StartTalkUseCase talks;

    @EventListener(ApplicationReadyEvent.class)
    void endOpenTalks() {
        talks.endAllOpen();
    }
}
