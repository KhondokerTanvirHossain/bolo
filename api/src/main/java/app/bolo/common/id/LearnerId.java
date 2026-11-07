package app.bolo.common.id;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

/** Identity shared by every module; lives in common so modules never import learner internals. */
public record LearnerId(@JsonValue UUID value) {

    public static LearnerId newId() {
        return new LearnerId(UUID.randomUUID());
    }

    public static LearnerId of(String value) {
        return new LearnerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
