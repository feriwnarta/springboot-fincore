package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.Timestamp;

import com.fincore.loan.domain.model.LoanOutboxEntry;
import com.fincore.loan.domain.port.LoanOutboxRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanOutboxRepository implements LoanOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void append(LoanOutboxEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (event_id, aggregate_id, aggregate_version, event_type, schema_version,
                     business_reference, payload, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?)
                """,
                entry.eventId(), entry.aggregateId(), entry.aggregateVersion(), entry.eventType(),
                entry.schemaVersion(), entry.businessReference(), entry.payloadJson(),
                Timestamp.from(entry.createdAt()));
    }
}
