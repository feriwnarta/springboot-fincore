package com.fincore.loan.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fincore.loan.application.query.LoanOutboxMessage;

public interface LoanOutboxRelayRepository {

    List<LoanOutboxMessage> claimBatch(int limit, Instant now, Duration lease, UUID claimToken);

    void markPublished(UUID eventId, UUID claimToken, Instant publishedAt);

    void markFailed(UUID eventId, UUID claimToken, Instant failedAt, String errorMessage);
}
