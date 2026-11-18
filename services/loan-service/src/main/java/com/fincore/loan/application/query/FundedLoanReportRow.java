package com.fincore.loan.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FundedLoanReportRow(
        UUID loanId,
        String customerId,
        String productCode,
        BigDecimal principalAmount,
        Instant fundedAt) {

    public FundedLoanReportRow {
        Objects.requireNonNull(loanId, "loanId must not be null");
        requireText(customerId, "customerId");
        requireText(productCode, "productCode");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(fundedAt, "fundedAt must not be null");
        if (principalAmount.signum() <= 0) {
            throw new IllegalArgumentException("principalAmount must be greater than zero");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
