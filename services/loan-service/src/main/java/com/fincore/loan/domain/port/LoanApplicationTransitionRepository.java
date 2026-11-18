package com.fincore.loan.domain.port;

import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.domain.enums.LoanApplicationStatus;

public interface LoanApplicationTransitionRepository {

    boolean transition(UUID id, long expectedVersion, LoanApplicationStatus targetStatus, Instant transitionedAt);
}
