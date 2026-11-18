package com.fincore.platform.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
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
        Map<String, Object> metadata,
        Instant occurredAt) {

    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
        if (aggregateVersion < 1) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        requireText(action, "action");
        requireText(outcome, "outcome");
        requireText(actorId, "actorId");
        requireText(actorRole, "actorRole");
        requireText(correlationId, "correlationId");
        requireText(businessReference, "businessReference");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
