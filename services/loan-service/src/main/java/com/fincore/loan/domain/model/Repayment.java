package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fincore.loan.domain.enums.RepaymentStatus;

public record Repayment(
        UUID id,
        UUID loanId,
        String businessReference,
        BigDecimal amount,
        RepaymentStatus status,
        String simulatedReceiptReference,
        String ledgerReference,
        String idempotencyKey,
        Instant createdAt,
        Instant postedAt) {

    public Repayment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanId, "loanId must not be null");
        requireText(businessReference, "businessReference");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        requireText(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
