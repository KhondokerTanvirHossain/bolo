package app.bolo.learner.application.service;

import app.bolo.learner.application.port.in.LearnerView;
import app.bolo.learner.application.port.in.SignInConfig;
import app.bolo.learner.application.port.in.VoiceAccessUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class VoiceAccessService implements VoiceAccessUseCase {

    private final SignInConfig signIn;
    private final boolean guestsMayTalk;

    VoiceAccessService(SignInConfig signIn, @Value("${bolo.guests.may-talk:false}") boolean guestsMayTalk) {
        this.signIn = signIn;
        this.guestsMayTalk = guestsMayTalk;
    }

    @Override
    public boolean guestMayUseVoice(LearnerView learner) {
        if (!"guest".equals(learner.kind()) || learner.unlimited()) return true;
        return guestsMayTalk && !signIn.enabled();
    }
}
