package com.fincore.loan.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableKafka
@EnableConfigurationProperties({LoanKafkaProperties.class, LoanOutboxRelayProperties.class})
public class LoanOutboxRelayConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
