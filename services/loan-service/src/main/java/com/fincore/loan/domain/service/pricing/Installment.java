package com.fincore.loan.domain.service.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record Installment(
        int installmentNo,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal totalAmount) {

    public Installment {
        if (installmentNo < 1) {
            throw new IllegalArgumentException("installmentNo must be greater than zero");
        }
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(interestAmount, "interestAmount must not be null");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        if (principalAmount.signum() < 0 || interestAmount.signum() < 0 || totalAmount.signum() < 0) {
            throw new IllegalArgumentException("installment amounts must not be negative");
        }
    }
}
