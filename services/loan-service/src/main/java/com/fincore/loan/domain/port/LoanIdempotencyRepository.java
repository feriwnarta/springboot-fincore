package com.fincore.loan.domain.port;

import java.util.Optional;

import com.fincore.loan.domain.model.IdempotencyRecord;

public interface LoanIdempotencyRepository {

    boolean createIfAbsent(IdempotencyRecord record);

    Optional<IdempotencyRecord> findByKey(String callerScope, String operation, String idemKey);

    void markSucceeded(IdempotencyRecord record, String resultRef);
}
