package com.fincore.ledger.domain.model;

import com.fincore.ledger.domain.enums.IdempotencyStatus;

public record LedgerIdempotencyRecord(
        String callerScope,
        String operation,
        String idempotencyKey,
        String requestFingerprint,
        String resultReference,
        IdempotencyStatus status) {
}
