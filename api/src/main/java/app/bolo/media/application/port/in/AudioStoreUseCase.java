package app.bolo.media.application.port.in;

import java.util.Optional;

/** Learner audio at rest (encrypted). Keys look like "talk/{sessionId}/turn-1.wav". */
public interface AudioStoreUseCase {

    void put(String key, byte[] bytes);

    Optional<byte[]> get(String key);

    void deletePrefix(String prefix);

    /** Names of the folders directly under {@code prefix}, e.g. the talk ids under "talk/". */
    java.util.List<String> children(String prefix);

    void delete(String key);
}
