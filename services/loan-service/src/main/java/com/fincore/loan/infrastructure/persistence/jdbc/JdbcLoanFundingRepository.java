package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.enums.LedgerPostingStatus;
import com.fincore.loan.domain.enums.LoanFundingStatus;
import com.fincore.loan.domain.model.LoanFunding;
import com.fincore.loan.domain.port.LoanFundingRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanFundingRepository implements LoanFundingRepository {

    private static final RowMapper<LoanFunding> ROW_MAPPER = JdbcLoanFundingRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(LoanFunding funding) {
        jdbcTemplate.update("""
                INSERT INTO loans
                    (id, application_id, customer_id, product_code, principal_amount, status,
                     ledger_posting_status, funded_at, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                funding.id(), funding.applicationId(), funding.customerId(), funding.productCode(),
                funding.principalAmount(), funding.status().name(), funding.ledgerPostingStatus().name(),
                toTimestamp(funding.fundedAt()), funding.version(), toTimestamp(funding.createdAt()),
                toTimestamp(funding.updatedAt()));
    }

    @Override
    public Optional<LoanFunding> findByApplicationId(UUID applicationId) {
        List<LoanFunding> funding = jdbcTemplate.query("""
                SELECT id, application_id, customer_id, product_code, principal_amount, status,
                       ledger_posting_status, funded_at, version, created_at, updated_at
                FROM loans WHERE application_id = ?
                """, ROW_MAPPER, applicationId);
        return funding.stream().findFirst();
    }

    @Override
    public Optional<LoanFunding> findById(UUID loanId) {
        List<LoanFunding> funding = jdbcTemplate.query("""
                SELECT id, application_id, customer_id, product_code, principal_amount, status,
                       ledger_posting_status, funded_at, version, created_at, updated_at
                FROM loans WHERE id = ?
                """, ROW_MAPPER, loanId);
        return funding.stream().findFirst();
    }

    @Override
    public boolean markFunded(UUID loanId, long expectedVersion, Instant fundedAt, Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE loans
                SET status = 'FUNDED', funded_at = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND version = ? AND status = 'FUNDING_PENDING'
                """, toTimestamp(fundedAt), toTimestamp(updatedAt), loanId, expectedVersion) == 1;
    }

    @Override
    public boolean markFailed(UUID loanId, long expectedVersion, Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE loans
                SET status = 'FUNDING_FAILED', version = version + 1, updated_at = ?
                WHERE id = ? AND version = ? AND status = 'FUNDING_PENDING'
                """, toTimestamp(updatedAt), loanId, expectedVersion) == 1;
    }

    @Override
    public boolean markLedgerPosted(UUID loanId, Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE loans
                SET ledger_posting_status = 'POSTED', updated_at = ?
                WHERE id = ? AND status = 'FUNDED' AND ledger_posting_status = 'PENDING'
                """, toTimestamp(updatedAt), loanId) == 1;
    }

    @Override
    public boolean markPaidOff(UUID loanId, Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE loans
                SET status = 'PAID_OFF', version = version + 1, updated_at = ?
                WHERE id = ? AND status = 'FUNDED' AND ledger_posting_status = 'POSTED'
                """, toTimestamp(updatedAt), loanId) == 1;
    }

    private static LoanFunding mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LoanFunding(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("application_id", UUID.class),
                resultSet.getString("customer_id"),
                resultSet.getString("product_code"),
                resultSet.getBigDecimal("principal_amount"),
                LoanFundingStatus.valueOf(resultSet.getString("status")),
                LedgerPostingStatus.valueOf(resultSet.getString("ledger_posting_status")),
                resultSet.getLong("version"),
                timestampToInstant(resultSet, "funded_at"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static Instant timestampToInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
