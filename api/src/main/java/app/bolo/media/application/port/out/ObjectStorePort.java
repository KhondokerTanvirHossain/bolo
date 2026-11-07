package app.bolo.media.application.port.out;

import java.util.Optional;

public interface ObjectStorePort {

    void put(String key, byte[] bytes);

    Optional<byte[]> get(String key);

    void deletePrefix(String prefix);

    void delete(String key);
}
