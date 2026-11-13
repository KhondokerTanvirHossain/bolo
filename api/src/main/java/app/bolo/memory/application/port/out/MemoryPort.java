package app.bolo.memory.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.memory.domain.MemoryNote;

import java.util.Optional;
import java.util.UUID;

/** The learner's one memory row. */
public interface MemoryPort {

    record Row(boolean enabled, MemoryNote note, int talks, UUID lastTalkId) {
    }

    Optional<Row> find(LearnerId learner);

    /** Locks the learner's row for this transaction (select … for update), creating an empty enabled one first if missing. */
    Row lockOrCreate(LearnerId learner);

    void save(LearnerId learner, boolean enabled, MemoryNote note, int talks, UUID lastTalkId);

    /** A guest signed into an existing account: the account's note is kept, the guest's lines added up to the caps. */
    void move(LearnerId from, LearnerId to);
}
