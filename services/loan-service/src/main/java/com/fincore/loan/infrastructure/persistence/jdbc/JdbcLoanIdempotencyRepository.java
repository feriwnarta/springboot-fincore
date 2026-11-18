package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.model.IdempotencyRecord;
import com.fincore.loan.domain.port.LoanIdempotencyRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanIdempotencyRepository implements LoanIdempotencyRepository {

    private static final RowMapper<IdempotencyRecord> ROW_MAPPER = JdbcLoanIdempotencyRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean createIfAbsent(IdempotencyRecord record) {
        return jdbcTemplate.update("""
                INSERT INTO idempotency_records
                    (id, caller_scope, operation, idem_key, request_fingerprint, result_ref, status, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (caller_scope, operation, idem_key) DO NOTHING
                """,
                record.callerScope(), record.operation(), record.idemKey(), record.requestFingerprint(),
                record.resultRef(), record.status().name()) == 1;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String callerScope, String operation, String idemKey) {
        List<IdempotencyRecord> records = jdbcTemplate.query("""
                SELECT caller_scope, operation, idem_key, request_fingerprint, result_ref, status
                FROM idempotency_records
                WHERE caller_scope = ? AND operation = ? AND idem_key = ?
                """, ROW_MAPPER, callerScope, operation, idemKey);
        return records.stream().findFirst();
    }

    @Override
    public void markSucceeded(IdempotencyRecord record, String resultRef) {
        jdbcTemplate.update("""
                UPDATE idempotency_records
                SET status = 'SUCCEEDED', result_ref = ?, response_code = 201, completed_at = CURRENT_TIMESTAMP
                WHERE caller_scope = ? AND operation = ? AND idem_key = ?
                """, resultRef, record.callerScope(), record.operation(), record.idemKey());
    }

    private static IdempotencyRecord mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new IdempotencyRecord(
                resultSet.getString("caller_scope"),
                resultSet.getString("operation"),
                resultSet.getString("idem_key"),
                resultSet.getString("request_fingerprint"),
                resultSet.getString("result_ref"),
                IdempotencyStatus.valueOf(resultSet.getString("status")));
    }
}
