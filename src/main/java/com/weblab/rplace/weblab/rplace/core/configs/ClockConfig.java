package com.weblab.rplace.weblab.rplace.core.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // One clock for time-limited records, so tests can move it.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
