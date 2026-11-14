package app.bolo.reminder.adapter.in.scheduler;

import app.bolo.common.time.BoloClock;
import app.bolo.reminder.application.service.ReminderSendService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every minute: send the reminders whose time has come (PRG-5). */
@Component
@RequiredArgsConstructor
class ReminderJob {

    private final ReminderSendService sender;
    private final BoloClock clock;

    @Scheduled(fixedDelayString = "${bolo.reminder.job-ms}", initialDelayString = "${bolo.reminder.job-ms}")
    void run() {
        sender.sendDue(clock.now());
    }
}
