package com.fincore.loan.domain.port;

import java.util.List;
import java.util.UUID;

import com.fincore.loan.domain.model.RepaymentScheduleDraft;

public interface LoanRepaymentScheduleRepository {

    void saveIfAbsent(UUID loanId, List<RepaymentScheduleDraft> schedules);

    List<com.fincore.loan.domain.model.RepaymentSchedule> findOpenForUpdate(UUID loanId);

    boolean applyAllocation(
            UUID scheduleId,
            long expectedVersion,
            java.math.BigDecimal principalAmount,
            java.math.BigDecimal interestAmount);

    boolean allPaid(UUID loanId);
}
