package com.fincore.search.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LoanEventProjection(
        UUID loanId,
        String customerId,
        String productCode,
        String status,
        BigDecimal principalAmount,
        String description,
        int tenorMonths,
        String businessReference,
        Instant createdAt,
        long aggregateVersion,
        Instant occurredAt,
        UUID eventId,
        String eventType) {

    public LoanEventProjection {
        Objects.requireNonNull(loanId, "loanId must not be null");
        Objects.requireNonNull(productCode, "productCode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
    }

    public LoanEventProjection(
            UUID loanId, String customerId, String productCode, String status,
            BigDecimal principalAmount, String description, long aggregateVersion,
            Instant occurredAt, UUID eventId, String eventType) {
        this(loanId, customerId, productCode, status, principalAmount, description, 0,
                null, occurredAt, aggregateVersion, occurredAt, eventId, eventType);
    }

    public static boolean isLifecycleEvent(String eventType) {
        return switch (eventType) {
            case "LoanSubmitted", "LoanApproved", "LoanRejected", "LoanFunded" -> true;
            default -> false;
        };
    }

    public static int lifecyclePrecedence(String typeOrStatus) {
        return switch (typeOrStatus) {
            case "LoanSubmitted", "SUBMITTED" -> 1;
            case "LoanApproved", "APPROVED" -> 2;
            case "LoanRejected", "REJECTED" -> 3;
            case "LoanFunded", "FUNDED" -> 4;
            case null, default -> 0;
        };
    }
}
