package com.fincore.search.api.dto.response;

import java.time.Instant;

public record SavingsAccountSearchResponse(
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
