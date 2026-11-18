package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record RepaymentScheduleDraft(
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue) {

    public RepaymentScheduleDraft {
        if (installmentNo < 1) {
            throw new IllegalArgumentException("installmentNo must be greater than zero");
        }
        Objects.requireNonNull(dueDate, "dueDate must not be null");
        Objects.requireNonNull(principalDue, "principalDue must not be null");
        Objects.requireNonNull(interestDue, "interestDue must not be null");
        if (principalDue.signum() < 0 || interestDue.signum() < 0) {
            throw new IllegalArgumentException("schedule amounts must not be negative");
        }
        if (principalDue.add(interestDue).signum() <= 0) {
            throw new IllegalArgumentException("schedule total must be greater than zero");
        }
    }
}
