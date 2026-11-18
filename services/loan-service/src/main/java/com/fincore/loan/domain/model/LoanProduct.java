package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fincore.loan.domain.enums.InterestType;

public record LoanProduct(
        UUID id,
        String code,
        InterestType interestType,
        BigDecimal annualRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean active,
        long version,
        Instant updatedAt) {

    public LoanProduct {
        Objects.requireNonNull(id, "id must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        Objects.requireNonNull(interestType, "interestType must not be null");
        Objects.requireNonNull(annualRate, "annualRate must not be null");
        Objects.requireNonNull(minAmount, "minAmount must not be null");
        Objects.requireNonNull(maxAmount, "maxAmount must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (annualRate.signum() < 0) {
            throw new IllegalArgumentException("annualRate must not be negative");
        }
        if (minAmount.signum() <= 0) {
            throw new IllegalArgumentException("minAmount must be greater than zero");
        }
        if (maxAmount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException("maxAmount must be greater than or equal to minAmount");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be greater than zero");
        }
    }
}
