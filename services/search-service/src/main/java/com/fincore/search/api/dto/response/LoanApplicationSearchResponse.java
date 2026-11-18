package com.fincore.search.api.dto.response;

import java.time.Instant;

public record LoanApplicationSearchResponse(
        String applicationId,
        String customerId,
        String productCode,
        String principalAmount,
        int tenorMonths,
        String status,
        long version,
        String businessReference,
        Instant createdAt) {
}
