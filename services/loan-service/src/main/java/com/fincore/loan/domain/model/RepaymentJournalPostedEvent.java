package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RepaymentJournalPostedEvent(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        String businessReference,
        Instant occurredAt,
        UUID correlationId,
        UUID journalId,
        UUID repaymentId,
        BigDecimal amount,
        String currency) {

    public RepaymentJournalPostedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(businessReference, "businessReference must not be blank");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(journalId, "journalId must not be null");
        Objects.requireNonNull(repaymentId, "repaymentId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }
}
