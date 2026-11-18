package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fincore.loan.domain.enums.LedgerPostingStatus;
import com.fincore.loan.domain.enums.LoanFundingStatus;

public record LoanFunding(
        UUID id,
        UUID applicationId,
        String customerId,
        String productCode,
        BigDecimal principalAmount,
        LoanFundingStatus status,
        LedgerPostingStatus ledgerPostingStatus,
        long version,
        Instant fundedAt,
        Instant createdAt,
        Instant updatedAt) {

    public LoanFunding {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        requireText(customerId, "customerId");
        requireText(productCode, "productCode");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(ledgerPostingStatus, "ledgerPostingStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (principalAmount.signum() <= 0) {
            throw new IllegalArgumentException("principalAmount must be greater than zero");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
