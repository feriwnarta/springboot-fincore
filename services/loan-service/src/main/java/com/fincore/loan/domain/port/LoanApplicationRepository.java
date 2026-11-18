package com.fincore.loan.domain.port;

import java.util.Optional;
import java.util.UUID;

import java.time.Instant;

import com.fincore.loan.domain.enums.LoanApplicationStatus;
import com.fincore.loan.domain.model.LoanApplication;

public interface LoanApplicationRepository {

    void save(LoanApplication application);

    Optional<LoanApplication> findById(UUID id);

    java.util.List<LoanApplication> findPage(
            String customerId,
            LoanApplicationStatus status,
            String productCode,
            Instant beforeCreatedAt,
            UUID beforeId,
            int limit);
}
