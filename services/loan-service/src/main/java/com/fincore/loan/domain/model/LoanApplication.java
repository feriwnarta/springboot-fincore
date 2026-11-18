package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fincore.loan.domain.enums.LoanApplicationStatus;

public record LoanApplication(
        UUID id,
        String customerId,
        String productCode,
        BigDecimal principalAmount,
        int tenorMonths,
        LoanApplicationStatus status,
        long version,
        Instant createdAt,
        Instant approvedAt,
        Instant rejectedAt,
        String actorId,
        String businessReference) {

    public LoanApplication {
        Objects.requireNonNull(id, "id must not be null");
        requireText(customerId, "customerId");
        requireText(productCode, "productCode");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireText(actorId, "actorId");
        requireText(businessReference, "businessReference");
        if (principalAmount.signum() <= 0) {
            throw new IllegalArgumentException("principalAmount must be greater than zero");
        }
        if (tenorMonths < 1) {
            throw new IllegalArgumentException("tenorMonths must be greater than zero");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be greater than zero");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
