package app.bolo.assessment.application.port.in;

import java.util.List;
import java.util.Optional;

/** Minimal pairs for a sound pattern key (e.g. "V_BH"), for the Coach's drills (DRL-2). */
public interface MinimalPairsUseCase {

    /** listen: {a, b, target}; say: 5 words. */
    record Pairs(List<String[]> listen, List<String> say) {
    }

    Optional<Pairs> pairs(String pattern);
}
