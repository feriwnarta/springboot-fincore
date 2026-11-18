package com.fincore.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RepaymentConfirmedEvent(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        String businessReference,
        Instant occurredAt,
        UUID correlationId,
        UUID repaymentId,
        BigDecimal amount,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        String currency) {

    public RepaymentConfirmedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(businessReference, "businessReference must not be blank");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(repaymentId, "repaymentId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(interestAmount, "interestAmount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() <= 0 || principalAmount.signum() < 0 || interestAmount.signum() < 0
                || amount.compareTo(principalAmount.add(interestAmount)) != 0) {
            throw new IllegalArgumentException("repayment amounts must be balanced");
        }
    }
}
