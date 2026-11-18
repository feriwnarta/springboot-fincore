package com.fincore.ledger.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({LedgerKafkaProperties.class, LedgerOutboxRelayProperties.class})
public class LedgerOutboxRelayConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
