package app.bolo.common.id;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

public record TalkSessionId(@JsonValue UUID value) {

    public static TalkSessionId newId() {
        return new TalkSessionId(UUID.randomUUID());
    }

    public static TalkSessionId of(String value) {
        return new TalkSessionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
