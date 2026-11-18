package com.fincore.ledger.domain.port;

import java.util.Optional;

import com.fincore.ledger.domain.model.LedgerIdempotencyRecord;

public interface LedgerIdempotencyRepository {

    boolean createIfAbsent(LedgerIdempotencyRecord record);

    Optional<LedgerIdempotencyRecord> findByKey(String callerScope, String operation, String idempotencyKey);

    void markSucceeded(LedgerIdempotencyRecord record, String resultReference);
}
