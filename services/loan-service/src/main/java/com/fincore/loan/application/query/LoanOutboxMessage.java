package com.fincore.loan.application.query;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LoanOutboxMessage(
        UUID eventId,
        UUID aggregateId,
        String eventType,
        String businessReference,
        String payloadJson,
        UUID claimToken,
        Instant claimedAt) {

    public LoanOutboxMessage {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        requireText(eventType, "eventType");
        requireText(businessReference, "businessReference");
        requireText(payloadJson, "payloadJson");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(claimedAt, "claimedAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
