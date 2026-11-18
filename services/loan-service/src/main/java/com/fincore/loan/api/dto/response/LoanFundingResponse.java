package com.fincore.loan.api.dto.response;

import java.time.Instant;

public record LoanFundingResponse(
        String loanId,
        String applicationId,
        String customerId,
        String productCode,
        String principalAmount,
        String fundingStatus,
        String ledgerPostingStatus,
        long version,
        Instant fundedAt,
        Instant createdAt,
        Instant updatedAt) {
}
