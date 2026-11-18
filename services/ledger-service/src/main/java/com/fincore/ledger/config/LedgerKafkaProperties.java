package com.fincore.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fincore.kafka")
public record LedgerKafkaProperties(String ledgerTopic) {

    public LedgerKafkaProperties {
        if (ledgerTopic == null || ledgerTopic.isBlank()) {
            throw new IllegalArgumentException("ledgerTopic must not be blank");
        }
    }
}
