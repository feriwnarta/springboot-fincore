package com.fincore.ledger.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerOutboxEntry(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int schemaVersion,
        String businessReference,
        String payload,
        Instant createdAt) {

    public LedgerOutboxEntry {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        if (aggregateVersion < 1) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        requireText(eventType, "eventType");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(businessReference, "businessReference");
        requireText(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
