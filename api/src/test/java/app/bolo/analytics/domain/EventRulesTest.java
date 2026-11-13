package app.bolo.analytics.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventRulesTest {

    @Test
    void allowListedNamesOnly() {
        assertThat(EventRules.problem("first_word_spoken", Map.of("seconds_since_first_open", 42))).isEmpty();
        assertThat(EventRules.problem("transcript", Map.of())).isPresent();
        assertThat(EventRules.problem("session_started", Map.of())).as("server-only name").isPresent();
    }

    /** §10: events carry no audio and no transcript text, so free text can't get in. */
    @Test
    void textCannotBeSmuggledIn() {
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("step", "a".repeat(41)))).isPresent();
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("step", "Hello."))).isPresent();
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("step", "Dhaka"))).isPresent();
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("step", Map.of("a", 1)))).isPresent();
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("step", List.of("a")))).isPresent();
        Map<String, Object> nine = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) nine.put("k" + i, i);
        assertThat(EventRules.problem("onboarding_step_completed", nine)).isPresent();
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("Bad Key", 1))).isPresent();
    }

    @Test
    void numbersBooleansAndShortKeysPass() {
        assertThat(EventRules.problem("mic_permission_result", Map.of("result", "granted"))).isEmpty();
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("step", "day1", "duration_s", 12.5))).isEmpty();
        assertThat(EventRules.problem("debrief_viewed", Map.of("fixes_count", 3, "first_session", true))).isEmpty();
    }

    /** Review I1: a short lower-case sentence or a phone number must not get into the event table. */
    @Test
    void sentencesAndPhoneNumbersCannotGetIn() {
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("step", "my name is rahim i live in mirpur 10"))).isPresent();
        assertThat(EventRules.problem("onboarding_step_completed", Map.of("duration_s", 1711223344L))).isPresent();
        assertThat(EventRules.problem("first_word_spoken", Map.of("seconds_since_first_open", 86_400))).isEmpty();
    }

    /** A talk that the browser had to close (mic or audio setup failed, playback threw, the socket dropped). */
    @Test
    void theBrowserMayReportWhyATalkStopped() {
        assertThat(EventRules.problem("talk_client_error", Map.of("stage", "audio_setup", "error", "notallowederror"))).isEmpty();
        assertThat(EventRules.problem("talk_client_error", Map.of("stage", "socket_closed", "code", 1006, "by_server", false))).isEmpty();
    }
}
