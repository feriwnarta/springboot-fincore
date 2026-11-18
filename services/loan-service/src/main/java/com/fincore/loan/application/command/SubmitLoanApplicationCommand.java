package com.fincore.loan.application.command;

import java.math.BigDecimal;
import java.util.Objects;

public record SubmitLoanApplicationCommand(
        String customerId,
        String productCode,
        BigDecimal principalAmount,
        int tenorMonths,
        String idempotencyKey,
        String actorId,
        String actorRole,
        String correlationId,
        String requestId) {

    public SubmitLoanApplicationCommand {
        requireText(customerId, "customerId");
        requireText(productCode, "productCode");
        Objects.requireNonNull(principalAmount, "principalAmount must not be null");
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
