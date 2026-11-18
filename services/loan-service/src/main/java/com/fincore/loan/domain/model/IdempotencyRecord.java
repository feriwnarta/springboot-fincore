package com.fincore.loan.domain.model;

import com.fincore.loan.domain.enums.IdempotencyStatus;

public record IdempotencyRecord(
        String callerScope,
        String operation,
        String idemKey,
        String requestFingerprint,
        String resultRef,
        IdempotencyStatus status) {
}
