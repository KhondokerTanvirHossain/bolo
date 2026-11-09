package app.bolo.learner.application.service;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.FreePlanKeyUseCase;
import app.bolo.learner.application.port.out.AccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class FreePlanKeyService implements FreePlanKeyUseCase {

    private final AccountPort accounts;

    @Override
    public Optional<String> freePlanKey(LearnerId learner) {
        return accounts.uidOf(learner).map(FreePlanKeyService::hash);
    }

    static String hash(String uid) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("bolo-free:" + uid).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
