package com.fincore.loan.infrastructure.persistence.jdbc;

import java.util.List;
import java.util.UUID;

import com.fincore.loan.domain.enums.RepaymentScheduleStatus;
import com.fincore.loan.domain.model.RepaymentSchedule;
import com.fincore.loan.domain.model.RepaymentScheduleDraft;
import com.fincore.loan.domain.port.LoanRepaymentScheduleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanRepaymentScheduleRepository implements LoanRepaymentScheduleRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveIfAbsent(UUID loanId, List<RepaymentScheduleDraft> schedules) {
        for (RepaymentScheduleDraft schedule : schedules) {
            jdbcTemplate.update("""
                    INSERT INTO repayment_schedules
                        (id, loan_id, installment_no, due_date, principal_due, interest_due,
                         principal_paid, interest_paid, status, version)
                    VALUES (?, ?, ?, ?, ?, ?, 0, 0, 'SCHEDULED', 1)
                    ON CONFLICT (loan_id, installment_no) DO NOTHING
                    """,
                    UUID.randomUUID(), loanId, schedule.installmentNo(), schedule.dueDate(),
                    schedule.principalDue(), schedule.interestDue());
        }
    }

    @Override
    public List<RepaymentSchedule> findOpenForUpdate(UUID loanId) {
        return jdbcTemplate.query("""
                SELECT id, loan_id, installment_no, due_date, principal_due, interest_due,
                       principal_paid, interest_paid, status, version
                FROM repayment_schedules
                WHERE loan_id = ? AND status <> 'PAID'
                ORDER BY due_date, installment_no
                FOR UPDATE
                """, (resultSet, rowNumber) -> new RepaymentSchedule(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("loan_id", UUID.class),
                resultSet.getInt("installment_no"),
                resultSet.getDate("due_date").toLocalDate(),
                resultSet.getBigDecimal("principal_due"),
                resultSet.getBigDecimal("interest_due"),
                resultSet.getBigDecimal("principal_paid"),
                resultSet.getBigDecimal("interest_paid"),
                RepaymentScheduleStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version")), loanId);
    }

    @Override
    public boolean applyAllocation(
            UUID scheduleId, long expectedVersion,
            java.math.BigDecimal principalAmount, java.math.BigDecimal interestAmount) {
        return jdbcTemplate.update("""
                UPDATE repayment_schedules
                SET principal_paid = principal_paid + ?,
                    interest_paid = interest_paid + ?,
                    status = CASE
                        WHEN principal_paid + ? >= principal_due
                             AND interest_paid + ? >= interest_due THEN 'PAID'
                        ELSE 'PARTIALLY_PAID'
                    END,
                    version = version + 1
                WHERE id = ? AND version = ?
                """, principalAmount, interestAmount, principalAmount, interestAmount,
                scheduleId, expectedVersion) == 1;
    }

    @Override
    public boolean allPaid(UUID loanId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT NOT EXISTS (
                    SELECT 1 FROM repayment_schedules
                    WHERE loan_id = ? AND status <> 'PAID'
                )
                """, Boolean.class, loanId);
        return Boolean.TRUE.equals(result);
    }
}
