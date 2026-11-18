package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.Timestamp;

import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.port.LoanAuditRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanAuditRepository implements LoanAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void append(LoanAuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO loan_audit_events
                    (id, event_id, aggregate_type, aggregate_id, aggregate_version, action,
                     outcome, actor_id, actor_role, correlation_id, causation_id,
                     business_reference, reason_code, metadata, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                entry.id(), entry.eventId(), entry.aggregateType(), entry.aggregateId(),
                entry.aggregateVersion(), entry.action(), entry.outcome(), entry.actorId(),
                entry.actorRole(), entry.correlationId(), entry.causationId(),
                entry.businessReference(), entry.reasonCode(), entry.metadataJson(),
                Timestamp.from(entry.occurredAt()));
    }
}
