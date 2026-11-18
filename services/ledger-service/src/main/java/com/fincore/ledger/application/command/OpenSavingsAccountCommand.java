package com.fincore.ledger.application.command;

public record OpenSavingsAccountCommand(
        String customerId,
        String idempotencyKey,
        String actorId,
        String actorRole,
        String correlationId,
        String requestId) {
}
