package com.fincore.loan.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RepaymentReportRow(
        UUID repaymentId,
        UUID loanId,
        BigDecimal amount,
        String status,
        Instant postedAt) {

    public RepaymentReportRow {
        Objects.requireNonNull(repaymentId, "repaymentId must not be null");
        Objects.requireNonNull(loanId, "loanId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
    }
}
