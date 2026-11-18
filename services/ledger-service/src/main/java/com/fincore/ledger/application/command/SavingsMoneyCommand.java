package com.fincore.ledger.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record SavingsMoneyCommand(
        UUID accountId,
        BigDecimal amount,
        String idempotencyKey,
        String actorId,
        String actorRole,
        String correlationId,
        String requestId) {
}
