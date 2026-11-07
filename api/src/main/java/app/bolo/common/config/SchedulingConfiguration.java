package app.bolo.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduling is switched on here so no feature module owns it (medscribe convention). */
@Configuration
@EnableScheduling
class SchedulingConfiguration {
}
