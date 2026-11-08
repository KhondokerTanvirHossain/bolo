package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.learner.application.port.out.InvitePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The closed beta (bolo.beta.invites-required): someone new needs an invite code; refuses with "invite". */
@Component
class InviteGate {

    private final InvitePort invites;
    private final boolean required;

    InviteGate(InvitePort invites, @Value("${bolo.beta.invites-required:false}") boolean required) {
        this.invites = invites;
        this.required = required;
    }

    void admitNewcomer(String code) {
        if (required && !invites.consume(code)) throw new RefusedException("invite");
    }
}
