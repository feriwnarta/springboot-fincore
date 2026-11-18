package com.fincore.loan.application.service;

import java.util.UUID;

import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.model.IdempotencyRecord;
import com.fincore.platform.web.ConflictException;

final class IdempotencyReplay {

    private IdempotencyReplay() {
    }

    static UUID resultId(IdempotencyRecord requested, IdempotencyRecord existing) {
        if (!requested.requestFingerprint().equals(existing.requestFingerprint())) {
            throw new ConflictException("Idempotency key was already used with a different request");
        }
        if (existing.status() != IdempotencyStatus.SUCCEEDED || existing.resultRef() == null) {
            throw new ConflictException("Idempotency request is being processed");
        }
        try {
            return UUID.fromString(existing.resultRef());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("Idempotency result is unavailable");
        }
    }
}
