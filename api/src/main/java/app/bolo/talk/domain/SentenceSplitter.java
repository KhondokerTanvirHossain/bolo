package app.bolo.talk.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cuts Bolo's streamed reply into sentences so speech can start after the first one. A sentence ends at . ! or ?
 * followed by whitespace; pieces under 20 characters ride along with the next one, which keeps TTS calls few.
 */
public class SentenceSplitter {

    static final int MIN_CHARS = 20;

    private final StringBuilder buffer = new StringBuilder();
    private int held;

    public List<String> push(String delta) {
        buffer.append(delta);
        List<String> out = new ArrayList<>();
        for (int i = held; i < buffer.length() - 1; i++) {
            char c = buffer.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(buffer.charAt(i + 1))) {
                if (buffer.substring(0, i + 1).trim().length() >= MIN_CHARS) {
                    out.add(buffer.substring(0, i + 1).trim());
                    buffer.delete(0, i + 1);
                    i = -1;
                    held = 0;
                } else {
                    held = i + 1;
                }
            }
        }
        return out;
    }

    public Optional<String> flush() {
        String rest = buffer.toString().trim();
        buffer.setLength(0);
        held = 0;
        return rest.isEmpty() ? Optional.empty() : Optional.of(rest);
    }
}
