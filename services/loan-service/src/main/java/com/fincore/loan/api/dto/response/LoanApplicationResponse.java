package com.fincore.loan.api.dto.response;

import java.time.Instant;

public record LoanApplicationResponse(
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
