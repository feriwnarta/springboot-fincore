package com.fincore.ledger.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fincore.ledger.application.query.LedgerOutboxMessage;

public interface LedgerOutboxRelayRepository {

    List<LedgerOutboxMessage> claimBatch(int limit, Instant now, Duration lease, UUID claimToken);

    void markPublished(UUID eventId, UUID claimToken, Instant publishedAt);

    void markFailed(UUID eventId, UUID claimToken, Instant failedAt, String errorMessage);
}
