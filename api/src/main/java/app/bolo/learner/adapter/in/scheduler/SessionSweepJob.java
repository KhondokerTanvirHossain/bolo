package app.bolo.learner.adapter.in.scheduler;

import app.bolo.learner.application.port.in.SweepSessionsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SessionSweepJob {

    private final SweepSessionsUseCase sweep;

    /** 04:15 Bangladesh time, the quietest hour. */
    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Dhaka")
    void run() {
        sweep.sweep();
    }
}
