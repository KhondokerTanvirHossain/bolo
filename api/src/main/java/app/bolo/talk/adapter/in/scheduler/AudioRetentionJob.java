package app.bolo.talk.adapter.in.scheduler;

import app.bolo.talk.application.port.in.AudioRetentionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly, so "No" audio is gone within 25 h at most (NFR-14 asks for 24 h after the talk; the sweep runs past it). */
@Component
@RequiredArgsConstructor
class AudioRetentionJob {

    private final AudioRetentionUseCase retention;

    @Scheduled(cron = "0 20 * * * *", zone = "Asia/Dhaka")
    void sweep() {
        retention.sweep();
    }
}
