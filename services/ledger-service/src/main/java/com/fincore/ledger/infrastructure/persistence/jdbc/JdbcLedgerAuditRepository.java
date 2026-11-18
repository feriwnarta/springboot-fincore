package com.fincore.ledger.infrastructure.persistence.jdbc;

import java.sql.Timestamp;

import com.fincore.platform.audit.AuditEvent;
import com.fincore.ledger.domain.port.LedgerAuditRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLedgerAuditRepository implements LedgerAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void append(AuditEvent event) {
        jdbcTemplate.update(
                "INSERT INTO ledger_audit_events (id, event_id, aggregate_type, aggregate_id, "
                        + "aggregate_version, action, outcome, actor_id, actor_role, correlation_id, "
                        + "causation_id, business_reference, metadata, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)",
                event.eventId(),
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.aggregateVersion(),
                event.action(),
                event.outcome(),
                event.actorId(),
                event.actorRole(),
                event.correlationId(),
                event.causationId(),
                event.businessReference(),
                metadata(event),
                Timestamp.from(event.occurredAt()));
    }

    private String metadata(AuditEvent event) {
        return event.metadata().entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\""
                        + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
