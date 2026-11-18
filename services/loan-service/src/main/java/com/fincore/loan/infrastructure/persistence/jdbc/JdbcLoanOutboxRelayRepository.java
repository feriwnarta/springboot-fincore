package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fincore.loan.application.port.LoanOutboxRelayRepository;
import com.fincore.loan.application.query.LoanOutboxMessage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanOutboxRelayRepository implements LoanOutboxRelayRepository {

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT event_id
                FROM outbox_events
                WHERE status IN ('PENDING', 'FAILED')
                   OR (status = 'IN_FLIGHT' AND claimed_at < ?)
                ORDER BY created_at, event_id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            ), claimed AS (
                UPDATE outbox_events event
                SET status = 'IN_FLIGHT', claim_token = ?, claimed_at = ?
                FROM candidates
                WHERE event.event_id = candidates.event_id
                RETURNING event.event_id, event.aggregate_id, event.event_type, event.business_reference,
                          event.payload::text AS payload_json, event.claim_token, event.claimed_at,
                          event.created_at
            )
            SELECT event_id, aggregate_id, event_type, business_reference, payload_json, claim_token, claimed_at
            FROM claimed
            ORDER BY created_at, event_id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<LoanOutboxMessage> claimBatch(int limit, Instant now, Duration lease, UUID claimToken) {
        Timestamp expiredAt = Timestamp.from(now.minus(lease));
        Timestamp claimedAt = Timestamp.from(now);
        return jdbcTemplate.query(
                CLAIM_SQL,
                (resultSet, rowNumber) -> new LoanOutboxMessage(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getObject("aggregate_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getString("business_reference"),
                        resultSet.getString("payload_json"),
                        resultSet.getObject("claim_token", UUID.class),
                        resultSet.getTimestamp("claimed_at").toInstant()),
                expiredAt, limit, claimToken, claimedAt);
    }

    @Override
    public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'PUBLISHED', published_at = ?, claim_token = NULL, claimed_at = NULL
                WHERE event_id = ? AND claim_token = ? AND status = 'IN_FLIGHT'
                """, Timestamp.from(publishedAt), eventId, claimToken);
    }

    @Override
    public void markFailed(UUID eventId, UUID claimToken, Instant failedAt, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'FAILED', retry_count = retry_count + 1, last_error = ?,
                    claim_token = NULL, claimed_at = NULL
                WHERE event_id = ? AND claim_token = ? AND status = 'IN_FLIGHT'
                """, errorMessage, eventId, claimToken);
    }
}
