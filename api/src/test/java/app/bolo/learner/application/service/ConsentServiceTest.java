package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.out.ConsentLedgerPort;
import app.bolo.learner.domain.Consent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConsentServiceTest {

    static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    final ConsentLedgerPort ledger = mock(ConsentLedgerPort.class);
    final ConsentService service = new ConsentService(ledger,
            new BoloClock(Clock.fixed(NOW, ZoneOffset.UTC)), "2026-11-v1");
    final LearnerId learner = LearnerId.newId();

    @Test
    void refusesWhenNotOver13() {
        assertThatThrownBy(() -> service.record(learner, true, false, "2026-11-v1"))
                .isInstanceOfSatisfying(RefusedException.class, e -> assertThat(e.code()).isEqualTo("under-13"));
        verify(ledger, never()).append(any());
    }

    @Test
    void refusesAnUnknownPolicyVersion() {
        assertThatThrownBy(() -> service.record(learner, true, true, "2025-01-v0"))
                .isInstanceOfSatisfying(RefusedException.class, e -> assertThat(e.code()).isEqualTo("policy-version"));
        verify(ledger, never()).append(any());
    }

    @Test
    void appendsAValidChoiceStampedWithNow() {
        service.record(learner, false, true, "2026-11-v1");

        ArgumentCaptor<Consent> saved = ArgumentCaptor.forClass(Consent.class);
        verify(ledger).append(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new Consent(learner, false, "2026-11-v1", NOW));
    }
}
