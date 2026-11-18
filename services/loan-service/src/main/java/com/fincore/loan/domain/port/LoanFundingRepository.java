package com.fincore.loan.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.model.LoanFunding;

public interface LoanFundingRepository {

    void save(LoanFunding funding);

    Optional<LoanFunding> findByApplicationId(UUID applicationId);

    Optional<LoanFunding> findById(UUID loanId);

    boolean markFunded(UUID loanId, long expectedVersion, Instant fundedAt, Instant updatedAt);

    boolean markFailed(UUID loanId, long expectedVersion, Instant updatedAt);

    boolean markLedgerPosted(UUID loanId, Instant updatedAt);

    boolean markPaidOff(UUID loanId, Instant updatedAt);
}
