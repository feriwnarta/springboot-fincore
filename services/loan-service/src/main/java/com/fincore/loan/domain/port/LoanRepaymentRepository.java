package com.fincore.loan.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.model.Repayment;
import com.fincore.loan.domain.model.RepaymentAllocation;

public interface LoanRepaymentRepository {

    Optional<Repayment> findByLoanAndIdempotencyKey(UUID loanId, String idempotencyKey);

    Optional<Repayment> findById(UUID repaymentId);

    List<RepaymentAllocation> findAllocations(UUID repaymentId);

    void save(Repayment repayment, List<RepaymentAllocation> allocations);

    boolean markPosted(UUID repaymentId, String ledgerReference, java.time.Instant postedAt);
}
