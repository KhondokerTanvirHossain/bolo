package app.bolo.media.application.port.out;

import java.util.Optional;

public interface ObjectStorePort {

    /** Names of the folders directly under {@code prefix} (e.g. talk ids under "talk/"). */
    java.util.List<String> children(String prefix);


    void put(String key, byte[] bytes);

    Optional<byte[]> get(String key);

    void deletePrefix(String prefix);

    void delete(String key);
}
