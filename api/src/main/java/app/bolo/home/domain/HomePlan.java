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

    /** kind "feedback" (talkId), "intro" (day: record the intro again, PRG-2), "reminder" or "practice" (pattern, title). */
    public record Card(String kind, String talkId, String pattern, String title, Integer day) {
    }

    private HomePlan() {
    }

    public static Optional<Card> card(Optional<LastDebrief> last) {
        return card(last, null);
    }

    public static Optional<Card> card(Optional<LastDebrief> last, Integer introDue) {
        return card(last, introDue, () -> false);
    }

    /**
     * US-6.9 priority: Finish your feedback → Re-record your intro ({@code introDue}) → Get a daily reminder (after a
     * ready feedback; {@code reminderDue} is asked only when the card would show, since asking counts a day) →
     * practise the last sound.
     */
    public static Optional<Card> card(Optional<LastDebrief> last, Integer introDue, java.util.function.BooleanSupplier reminderDue) {
        boolean ready = last.isPresent() && "ready".equals(last.get().status());
        if (ready && !last.get().viewed()) return Optional.of(new Card("feedback", last.get().talkId(), null, null, null));
        if (introDue != null) return Optional.of(new Card("intro", null, null, null, introDue));
        if (ready && reminderDue.getAsBoolean()) return Optional.of(new Card("reminder", null, null, null, null));
        if (ready && last.get().soundPattern() != null) {
            // US-5.1: "3-minute practice" with that talk's drills.
            return Optional.of(new Card("practice", last.get().talkId(), last.get().soundPattern(), last.get().soundTitle(), null));
        }
        return Optional.empty();
    }
}
