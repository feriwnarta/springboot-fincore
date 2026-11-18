package com.fincore.loan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fincore.kafka")
public record LoanKafkaProperties(String loanTopic, String ledgerTopic, String ledgerConsumerGroup) {

    public LoanKafkaProperties {
        if (loanTopic == null || loanTopic.isBlank()) {
            throw new IllegalArgumentException("loanTopic must not be blank");
        }
        if (ledgerTopic == null || ledgerTopic.isBlank()) {
            throw new IllegalArgumentException("ledgerTopic must not be blank");
        }
        if (ledgerConsumerGroup == null || ledgerConsumerGroup.isBlank()) {
            throw new IllegalArgumentException("ledgerConsumerGroup must not be blank");
        }
    }
}
