package com.fincore.loan.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fincore.kafka.outbox-relay")
public record LoanOutboxRelayProperties(
        boolean enabled,
        int batchSize,
        Duration claimLease,
        Duration sendTimeout) {

    public LoanOutboxRelayProperties {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("batchSize must be between 1 and 500");
        }
        requirePositive(claimLease, "claimLease");
        requirePositive(sendTimeout, "sendTimeout");
    }

    private static void requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
