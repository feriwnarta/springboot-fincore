package com.fincore.loan.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.fincore.loan.domain.enums.RepaymentScheduleStatus;

public record RepaymentSchedule(
        UUID id,
        UUID loanId,
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        RepaymentScheduleStatus status,
        long version) {

    public RepaymentSchedule {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanId, "loanId must not be null");
        Objects.requireNonNull(dueDate, "dueDate must not be null");
        Objects.requireNonNull(principalDue, "principalDue must not be null");
        Objects.requireNonNull(interestDue, "interestDue must not be null");
        Objects.requireNonNull(principalPaid, "principalPaid must not be null");
        Objects.requireNonNull(interestPaid, "interestPaid must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (installmentNo < 1 || version < 1) {
            throw new IllegalArgumentException("installmentNo and version must be positive");
        }
    }

    public BigDecimal outstandingInterest() {
        return interestDue.subtract(interestPaid).max(BigDecimal.ZERO);
    }

    public BigDecimal outstandingPrincipal() {
        return principalDue.subtract(principalPaid).max(BigDecimal.ZERO);
    }
}
