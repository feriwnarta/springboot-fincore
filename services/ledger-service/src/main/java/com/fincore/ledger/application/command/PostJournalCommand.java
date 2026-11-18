package com.fincore.ledger.application.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PostJournalCommand(
        String businessTransactionReference,
        String currency,
        String debitAccountCode,
        String creditAccountCode,
        BigDecimal amount,
        String actorId,
        String actorRole,
        String correlationId,
        String requestId,
        UUID aggregateId) {

    public PostJournalCommand(
            String businessTransactionReference,
            String currency,
            String debitAccountCode,
            String creditAccountCode,
            BigDecimal amount,
            String actorId,
            String actorRole,
            String correlationId,
            String requestId) {
        this(businessTransactionReference, currency, debitAccountCode, creditAccountCode, amount,
                actorId, actorRole, correlationId, requestId, null);
    }

    public PostJournalCommand {
        requireText(businessTransactionReference, "businessTransactionReference");
        requireText(currency, "currency");
        requireText(debitAccountCode, "debitAccountCode");
        requireText(creditAccountCode, "creditAccountCode");
        Objects.requireNonNull(amount, "amount must not be null");
        requireText(actorId, "actorId");
        requireText(actorRole, "actorRole");
        requireText(correlationId, "correlationId");
        requireText(requestId, "requestId");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (amount.scale() > 4) {
            throw new IllegalArgumentException("amount scale must not exceed 4");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
