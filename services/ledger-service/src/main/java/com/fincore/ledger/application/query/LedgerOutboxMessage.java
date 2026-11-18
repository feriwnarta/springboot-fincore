package com.fincore.ledger.application.query;

import java.time.Instant;
import java.util.UUID;

public record LedgerOutboxMessage(
        UUID eventId,
        UUID aggregateId,
        String eventType,
        String businessReference,
        String payload,
        UUID claimToken,
        Instant claimedAt) {
}
