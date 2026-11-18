package com.fincore.ledger.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.fincore.ledger.domain.enums.IdempotencyStatus;
import com.fincore.ledger.domain.model.LedgerIdempotencyRecord;
import com.fincore.ledger.domain.port.LedgerIdempotencyRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLedgerIdempotencyRepository implements LedgerIdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean createIfAbsent(LedgerIdempotencyRecord record) {
        return jdbcTemplate.update("""
                INSERT INTO ledger_idempotency_records
                    (id, caller_scope, operation, idem_key, request_fingerprint, result_ref, status, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (caller_scope, operation, idem_key) DO NOTHING
                """, record.callerScope(), record.operation(), record.idempotencyKey(),
                record.requestFingerprint(), record.resultReference(), record.status().name()) == 1;
    }

    @Override
    public Optional<LedgerIdempotencyRecord> findByKey(String callerScope, String operation, String idempotencyKey) {
        List<LedgerIdempotencyRecord> records = jdbcTemplate.query("""
                SELECT caller_scope, operation, idem_key, request_fingerprint, result_ref, status
                FROM ledger_idempotency_records
                WHERE caller_scope = ? AND operation = ? AND idem_key = ?
                """, (resultSet, rowNumber) -> map(resultSet), callerScope, operation, idempotencyKey);
        return records.stream().findFirst();
    }

    @Override
    public void markSucceeded(LedgerIdempotencyRecord record, String resultReference) {
        jdbcTemplate.update("""
                UPDATE ledger_idempotency_records
                SET status = 'SUCCEEDED', result_ref = ?, response_code = 201, completed_at = CURRENT_TIMESTAMP
                WHERE caller_scope = ? AND operation = ? AND idem_key = ?
                """, resultReference, record.callerScope(), record.operation(), record.idempotencyKey());
    }

    private static LedgerIdempotencyRecord map(ResultSet resultSet) throws SQLException {
        return new LedgerIdempotencyRecord(resultSet.getString("caller_scope"), resultSet.getString("operation"),
                resultSet.getString("idem_key"), resultSet.getString("request_fingerprint"),
                resultSet.getString("result_ref"), IdempotencyStatus.valueOf(resultSet.getString("status")));
    }
}
