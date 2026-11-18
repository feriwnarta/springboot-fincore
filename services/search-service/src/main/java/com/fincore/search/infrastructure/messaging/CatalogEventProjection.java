package com.fincore.search.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CatalogEventProjection(
        Type type,
        UUID aggregateId,
        String eventType,
        String code,
        String interestType,
        BigDecimal annualRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean active,
        String accountNumber,
        String customerId,
        String currency,
        String status,
        BigDecimal currentBalance,
        long aggregateVersion,
        Instant createdAt,
        Instant updatedAt,
        Instant occurredAt,
        UUID eventId) {

    public enum Type {
        LOAN_PRODUCT,
        SAVINGS_ACCOUNT
    }
}
