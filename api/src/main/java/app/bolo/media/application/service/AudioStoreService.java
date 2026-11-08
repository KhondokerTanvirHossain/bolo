package app.bolo.media.application.service;

import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.media.application.port.out.ObjectStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AudioStoreService implements AudioStoreUseCase {

    private final ObjectStorePort store;

    @Override
    public void put(String key, byte[] bytes) {
        store.put(key, bytes);
    }

    @Override
    public Optional<byte[]> get(String key) {
        return store.get(key);
    }

    @Override
    public void deletePrefix(String prefix) {
        store.deletePrefix(prefix);
    }

    @Override
    public void delete(String key) {
        store.delete(key);
    }

    @Override
    public java.util.List<String> children(String prefix) {
        return store.children(prefix);
    }
}
