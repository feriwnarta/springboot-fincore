package com.fincore.loan.application.command;

import java.math.BigDecimal;
import java.util.Objects;

import com.fincore.loan.domain.enums.InterestType;

public record CreateLoanProductCommand(
        String code,
        InterestType interestType,
        BigDecimal annualRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean active,
        String idempotencyKey,
        String correlationId,
        String requestId) {

    public CreateLoanProductCommand {
        requireText(code, "code");
        Objects.requireNonNull(interestType, "interestType must not be null");
        Objects.requireNonNull(annualRate, "annualRate must not be null");
        Objects.requireNonNull(minAmount, "minAmount must not be null");
        Objects.requireNonNull(maxAmount, "maxAmount must not be null");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(correlationId, "correlationId");
        requireText(requestId, "requestId");
        if (annualRate.signum() < 0) {
            throw new IllegalArgumentException("annualRate must not be negative");
        }
        if (minAmount.signum() <= 0) {
            throw new IllegalArgumentException("minAmount must be greater than zero");
        }
        if (maxAmount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException("maxAmount must be greater than or equal to minAmount");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
