package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.enums.RepaymentStatus;
import com.fincore.loan.domain.model.Repayment;
import com.fincore.loan.domain.model.RepaymentAllocation;
import com.fincore.loan.domain.port.LoanRepaymentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanRepaymentRepository implements LoanRepaymentRepository {

    private static final RowMapper<Repayment> ROW_MAPPER = JdbcLoanRepaymentRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<Repayment> findByLoanAndIdempotencyKey(UUID loanId, String idempotencyKey) {
        List<Repayment> repayments = jdbcTemplate.query("""
                SELECT id, loan_id, business_ref, amount, status, simulated_receipt_ref,
                       ledger_ref, idem_key, created_at, posted_at
                FROM repayments
                WHERE loan_id = ? AND idem_key = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, ROW_MAPPER, loanId, idempotencyKey);
        return repayments.stream().findFirst();
    }

    @Override
    public Optional<Repayment> findById(UUID repaymentId) {
        List<Repayment> repayments = jdbcTemplate.query("""
                SELECT id, loan_id, business_ref, amount, status, simulated_receipt_ref,
                       ledger_ref, idem_key, created_at, posted_at
                FROM repayments WHERE id = ?
                """, ROW_MAPPER, repaymentId);
        return repayments.stream().findFirst();
    }

    @Override
    public List<RepaymentAllocation> findAllocations(UUID repaymentId) {
        return jdbcTemplate.query("""
                SELECT a.schedule_id, a.principal_amount, a.interest_amount, s.version
                FROM repayment_allocations a
                JOIN repayment_schedules s ON s.id = a.schedule_id
                WHERE a.repayment_id = ?
                ORDER BY s.installment_no
                """, (resultSet, rowNumber) -> new RepaymentAllocation(
                resultSet.getObject("schedule_id", UUID.class),
                resultSet.getBigDecimal("principal_amount"),
                resultSet.getBigDecimal("interest_amount"),
                resultSet.getLong("version")), repaymentId);
    }

    @Override
    public void save(Repayment repayment, List<RepaymentAllocation> allocations) {
        jdbcTemplate.update("""
                INSERT INTO repayments
                    (id, loan_id, business_ref, amount, status, simulated_receipt_ref,
                     ledger_ref, idem_key, created_at, posted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                repayment.id(), repayment.loanId(), repayment.businessReference(), repayment.amount(),
                repayment.status().name(), repayment.simulatedReceiptReference(), repayment.ledgerReference(),
                repayment.idempotencyKey(), Timestamp.from(repayment.createdAt()),
                repayment.postedAt() == null ? null : Timestamp.from(repayment.postedAt()));
        for (RepaymentAllocation allocation : allocations) {
            jdbcTemplate.update("""
                    INSERT INTO repayment_allocations
                        (id, repayment_id, schedule_id, principal_amount, interest_amount)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), repayment.id(), allocation.scheduleId(),
                    allocation.principalAmount(), allocation.interestAmount());
        }
    }

    @Override
    public boolean markPosted(UUID repaymentId, String ledgerReference, java.time.Instant postedAt) {
        return jdbcTemplate.update("""
                UPDATE repayments
                SET status = 'POSTED', ledger_ref = ?, posted_at = ?
                WHERE id = ? AND status = 'CONFIRMED_AWAITING_LEDGER'
                """, ledgerReference, Timestamp.from(postedAt), repaymentId) == 1;
    }

    private static Repayment mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Repayment(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("loan_id", UUID.class),
                resultSet.getString("business_ref"),
                resultSet.getBigDecimal("amount"),
                RepaymentStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("simulated_receipt_ref"),
                resultSet.getString("ledger_ref"),
                resultSet.getString("idem_key"),
                resultSet.getTimestamp("created_at").toInstant(),
                timestampToInstant(resultSet, "posted_at"));
    }

    private static java.time.Instant timestampToInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
