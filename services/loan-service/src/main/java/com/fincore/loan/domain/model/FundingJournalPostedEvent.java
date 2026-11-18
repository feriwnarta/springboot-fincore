package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundingJournalPostedEvent(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        String businessReference,
        Instant occurredAt,
        UUID correlationId,
        UUID journalId,
        BigDecimal amount,
        String currency) {
}
