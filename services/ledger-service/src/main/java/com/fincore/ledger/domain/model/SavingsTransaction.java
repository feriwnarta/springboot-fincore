package com.fincore.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fincore.ledger.domain.enums.SavingsOperationType;

public record SavingsTransaction(
        UUID id,
        UUID accountId,
        String businessReference,
        SavingsOperationType operationType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID journalId,
        Instant createdAt) {
}
