package app.bolo.assessment.adapter.in.web;

import app.bolo.assessment.application.port.in.AttemptUseCase;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.ApiError;
import app.bolo.common.web.CurrentLearner;
import app.bolo.speech.application.port.in.SpeechBusyException;
import app.bolo.speech.application.port.in.SpeechUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
class AttemptController {

    private final AttemptUseCase attempts;

    /** US-4.3 "Try it" and the practice page: a short WAV (16 kHz mono) read against {@code reference}. */
    @PostMapping("/api/attempts")
    AttemptUseCase.Outcome attempt(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                   @RequestParam("audio") MultipartFile audio, @RequestParam("reference") String reference)
            throws IOException {
        if (learner == null) throw new SignedOutException();
        return attempts.attempt(learner, audio.getBytes(), reference);
    }

    @GetMapping("/api/practice")
    Map<String, List<AttemptUseCase.Practice>> practice(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return Map.of("patterns", attempts.practice());
    }

    @ExceptionHandler(SpeechBusyException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError busy() {
        return new ApiError("busy");
    }

    @ExceptionHandler(SpeechUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError unavailable() {
        return new ApiError("voice-unavailable");
    }
}
