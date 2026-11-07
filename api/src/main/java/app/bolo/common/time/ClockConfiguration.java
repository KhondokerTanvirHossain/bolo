package app.bolo.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ClockConfiguration {

    @Bean
    BoloClock boloClock() {
        return new BoloClock(Clock.systemUTC());
    }
}
