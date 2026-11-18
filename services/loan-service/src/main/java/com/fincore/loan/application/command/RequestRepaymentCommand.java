package com.fincore.loan.application.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RequestRepaymentCommand(
        UUID loanId,
        BigDecimal amount,
        String idempotencyKey,
        String actorId,
        String actorRole,
        String correlationId,
        String requestId) {

    public RequestRepaymentCommand {
        Objects.requireNonNull(loanId, "loanId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(actorId, "actorId");
        requireText(actorRole, "actorRole");
        requireText(correlationId, "correlationId");
        requireText(requestId, "requestId");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
