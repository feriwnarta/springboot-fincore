package com.fincore.loan.domain.model;

import java.time.Instant;
import java.util.UUID;

public record LoanOutboxEntry(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int schemaVersion,
        String businessReference,
        String payloadJson,
        Instant createdAt) {
}
