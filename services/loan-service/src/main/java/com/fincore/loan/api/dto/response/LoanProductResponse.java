package com.fincore.loan.api.dto.response;

import java.time.Instant;

public record LoanProductResponse(
        String id,
        String code,
        String interestType,
        String annualRate,
        String minAmount,
        String maxAmount,
        boolean active,
        long version,
        Instant updatedAt) {
}
