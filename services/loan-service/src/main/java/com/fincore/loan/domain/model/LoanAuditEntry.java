package com.fincore.loan.domain.model;

import java.time.Instant;
import java.util.UUID;

public record LoanAuditEntry(
        UUID id,
        UUID eventId,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String action,
        String outcome,
        String actorId,
        String actorRole,
        String correlationId,
        String causationId,
        String businessReference,
        String reasonCode,
        String metadataJson,
        Instant occurredAt) {
}
