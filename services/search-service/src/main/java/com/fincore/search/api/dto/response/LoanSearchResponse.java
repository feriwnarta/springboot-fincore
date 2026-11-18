package com.fincore.search.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanSearchResponse(
        String loanId,
        String customerId,
        String productCode,
        String status,
        BigDecimal principalAmount,
        String description,
        long aggregateVersion,
        Instant occurredAt) {
}
