package com.fincore.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LoanFundedEvent(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        String businessReference,
        Instant occurredAt,
        UUID correlationId,
        String customerId,
        String productCode,
        BigDecimal principalAmount,
        String currency) {

    public LoanFundedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        if (aggregateVersion < 1) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        requireText(businessReference, "businessReference");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        requireText(customerId, "customerId");
        requireText(productCode, "productCode");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        requireText(currency, "currency");
        if (principalAmount.signum() <= 0) {
            throw new IllegalArgumentException("principalAmount must be greater than zero");
        }
        if (principalAmount.scale() > 4) {
            throw new IllegalArgumentException("principalAmount scale must not exceed 4");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
