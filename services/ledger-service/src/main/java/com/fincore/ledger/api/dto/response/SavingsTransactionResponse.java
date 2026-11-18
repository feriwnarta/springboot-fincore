package com.fincore.ledger.api.dto.response;

import java.time.Instant;

public record SavingsTransactionResponse(
        String transactionId,
        String accountId,
        String businessReference,
        String operationType,
        String amount,
        String balanceAfter,
        String journalId,
        Instant createdAt) {
}
