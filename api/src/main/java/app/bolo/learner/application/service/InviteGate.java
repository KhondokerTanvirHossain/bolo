package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.learner.application.port.out.InvitePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The closed beta (bolo.beta.invites-required): someone new needs an invite code; refuses with "invite".
 * With invites off, a code is still read, only to tell whether it makes an unlimited learner (the probes, the founder).
 */
@Component
class InviteGate {

    private final InvitePort invites;
    private final boolean required;

    InviteGate(InvitePort invites, @Value("${bolo.beta.invites-required:false}") boolean required) {
        this.invites = invites;
        this.required = required;
    }

    /** @return whether the newcomer is unlimited (outside the free plan). */
    boolean admitNewcomer(String code) {
        var used = code == null || code.isBlank() ? java.util.Optional.<Boolean>empty() : invites.consume(code);
        if (required && used.isEmpty()) throw new RefusedException("invite");
        return used.orElse(false);
    }
}
