package com.fincore.ledger.application.command;

import java.util.UUID;

import com.fincore.ledger.domain.enums.SavingsAccountStatus;

public record ChangeSavingsStatusCommand(
        UUID accountId,
        SavingsAccountStatus status,
        String actorId,
        String actorRole,
        String correlationId,
        String requestId) {
}
