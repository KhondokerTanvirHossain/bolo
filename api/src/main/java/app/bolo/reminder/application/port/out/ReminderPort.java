package app.bolo.reminder.application.port.out;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderPort {

    record Settings(boolean enabled, String localTime, String timeZone, boolean keepPrivate, Instant askedAt, int ignored,
                    Instant stoppedAt, boolean comebackSent, LocalDate lastSentOn, Instant lastSentAt, UUID lastSentId,
                    boolean tapped, int cardDays, LocalDate cardLastOn, boolean cardDismissed, String line, String lineSource) {
    }

    record Device(String endpoint, String p256dh, String auth) {
    }

    Optional<Settings> find(LearnerId learner);

    /** Creates the default row if missing. */
    void ensure(LearnerId learner, Instant now);

    void saveChoices(LearnerId learner, boolean enabled, String localTime, String timeZone, boolean keepPrivate, Instant askedAt, Instant now);

    void clearBackOff(LearnerId learner, String timeZone, Instant now);

    /** Sets tapped when {@code sent} is the last reminder's id; true when it matched. */
    boolean markTapped(LearnerId learner, UUID sent);

    void countCardDay(LearnerId learner, LocalDate day);

    void dismissCard(LearnerId learner);

    void upsertDevice(LearnerId learner, String endpoint, String p256dh, String auth, String userAgent, Instant now);

    void deleteDevice(LearnerId learner, String endpoint);

    int deviceCount(LearnerId learner);

    List<Device> devices(LearnerId learner);

    void move(LearnerId from, LearnerId to);
}
