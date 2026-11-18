package com.fincore.search.api.dto.response;

import java.time.Instant;

public record LoanProductSearchResponse(
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
