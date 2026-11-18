package com.fincore.ledger.api.dto.response;

import java.time.Instant;

public record SavingsAccountResponse(
        String accountId,
        String accountNumber,
        String customerId,
        String currency,
        String status,
        String currentBalance,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
