package app.bolo.analytics.adapter.in.web;

import app.bolo.analytics.application.port.in.RecordEventUseCase;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
class EventController {

    private final RecordEventUseCase events;

    record EventRequest(String name, Map<String, Object> props) {
    }

    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void record(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @RequestBody EventRequest body) {
        if (learner == null) throw new SignedOutException();
        events.record(learner, body.name(), body.props());
    }
}
