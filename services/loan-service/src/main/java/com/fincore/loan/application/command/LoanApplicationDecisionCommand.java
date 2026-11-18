package com.fincore.loan.application.command;

import java.util.Objects;
import java.util.UUID;

public record LoanApplicationDecisionCommand(
        UUID applicationId,
        String idempotencyKey,
        String actorId,
        String actorRole,
        String correlationId,
        String requestId,
        String reasonCode,
        String reason) {

    public LoanApplicationDecisionCommand {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(actorId, "actorId");
        requireText(actorRole, "actorRole");
        requireText(correlationId, "correlationId");
        requireText(requestId, "requestId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
