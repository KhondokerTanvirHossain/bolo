package app.bolo.home.domain;

import java.util.Optional;

/**
 * HOM-1 / US-6.9: Home's one primary button (always Talk for now: a chat paused before a reconnect can't be resumed)
 * and at most one card: finish a ready feedback you haven't seen, else practise that feedback's sound, else nothing.
 */
public final class HomePlan {

    /** The latest debrief as Home needs it; soundPattern/soundTitle are null when it had no sound fix. */
    public record LastDebrief(String talkId, String status, boolean viewed, String soundPattern, String soundTitle) {
    }

    /** kind "feedback" (talkId) or "practice" (pattern, title). */
    public record Card(String kind, String talkId, String pattern, String title) {
    }

    private HomePlan() {
    }

    public static Optional<Card> card(Optional<LastDebrief> last) {
        if (last.isEmpty() || !"ready".equals(last.get().status())) return Optional.empty();
        LastDebrief d = last.get();
        if (!d.viewed()) return Optional.of(new Card("feedback", d.talkId(), null, null));
        if (d.soundPattern() != null) return Optional.of(new Card("practice", null, d.soundPattern(), d.soundTitle()));
        return Optional.empty();
    }
}
