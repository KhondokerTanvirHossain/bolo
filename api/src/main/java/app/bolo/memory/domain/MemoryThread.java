package app.bolo.memory.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Something to follow up (spec 2026-11-13): a plan, a worry or how their practice is going.
 * {@code said}: the Bangladesh date it was last mentioned. {@code due}: when a plan should have happened, if they said.
 * {@code openedAt}/{@code openedTalk}: when Bolo last opened a talk with it (it then rests, see {@link MemoryNote#opener}).
 */
public record MemoryThread(String id, Kind kind, String text, LocalDate said, LocalDate due, Instant openedAt, Integer openedTalk) {

    public enum Kind {
        @JsonProperty("plan") PLAN,
        @JsonProperty("worry") WORRY,
        @JsonProperty("prep") PREP;

        public String json() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public MemoryThread withText(String t) {
        return new MemoryThread(id, kind, t, said, due, openedAt, openedTalk);
    }

    public MemoryThread opened(Instant at, int talk) {
        return new MemoryThread(id, kind, text, said, due, at, talk);
    }
}
