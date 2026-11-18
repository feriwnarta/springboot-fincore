package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RepaymentAllocation(
        UUID scheduleId,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        long scheduleVersion) {

    public RepaymentAllocation {
        Objects.requireNonNull(scheduleId, "scheduleId must not be null");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(interestAmount, "interestAmount must not be null");
        if (principalAmount.signum() < 0 || interestAmount.signum() < 0
                || principalAmount.add(interestAmount).signum() <= 0) {
            throw new IllegalArgumentException("allocation amount must be greater than zero");
        }
        if (scheduleVersion < 1) {
            throw new IllegalArgumentException("scheduleVersion must be positive");
        }
    }
}
