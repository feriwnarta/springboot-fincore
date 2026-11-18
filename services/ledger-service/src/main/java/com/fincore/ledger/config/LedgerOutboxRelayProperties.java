package com.fincore.ledger.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fincore.kafka.outbox-relay")
public record LedgerOutboxRelayProperties(
        boolean enabled,
        Duration fixedDelay,
        int batchSize,
        Duration claimLease,
        Duration sendTimeout) {
}
