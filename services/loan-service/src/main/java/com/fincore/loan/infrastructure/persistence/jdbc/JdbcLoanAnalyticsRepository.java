package com.fincore.loan.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import java.util.UUID;

import com.fincore.loan.application.query.FundedLoanReportRow;
import com.fincore.loan.application.query.RepaymentAggregateReportRow;
import com.fincore.loan.application.query.RepaymentReportRow;
import com.fincore.loan.application.query.RunningFundedTotalReportRow;
import com.fincore.loan.application.query.TopFundedLoanReportRow;
import com.fincore.loan.application.port.LoanAnalyticsRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLoanAnalyticsRepository implements LoanAnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<FundedLoanReportRow> findFundedLoans(Instant from, Instant to, int limit) {
        return jdbcTemplate.query("""
                SELECT id, customer_id, product_code, principal_amount, funded_at
                FROM loans
                WHERE status = 'FUNDED'
                  AND funded_at >= ?
                  AND funded_at < ?
                ORDER BY funded_at, id
                LIMIT ?
                """, JdbcLoanAnalyticsRepository::mapFundedLoan,
                timestamp(from), timestamp(to), limit);
    }

    @Override
    public List<RepaymentReportRow> findRepayments(Instant from, Instant to, int limit) {
        return jdbcTemplate.query("""
                SELECT id, loan_id, amount, status, posted_at
                FROM repayments
                WHERE created_at >= ?
                  AND created_at < ?
                ORDER BY created_at, id
                LIMIT ?
                """, JdbcLoanAnalyticsRepository::mapRepayment,
                timestamp(from), timestamp(to), limit);
    }

    @Override
    public List<TopFundedLoanReportRow> findTopThreeFundedLoansPerCustomer(Instant from, Instant to) {
        return jdbcTemplate.query("""
                WITH ranked_loans AS (
                    SELECT id, customer_id, principal_amount, funded_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY customer_id
                               ORDER BY principal_amount DESC, id ASC
                           ) AS rn
                    FROM loans
                    WHERE status = 'FUNDED'
                      AND funded_at >= ?
                      AND funded_at < ?
                )
                SELECT id, customer_id, principal_amount, funded_at, rn
                FROM ranked_loans
                WHERE rn <= 3
                ORDER BY customer_id, rn
                """, (resultSet, rowNumber) -> new TopFundedLoanReportRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("customer_id"),
                resultSet.getBigDecimal("principal_amount"),
                resultSet.getTimestamp("funded_at").toInstant(),
                resultSet.getInt("rn")),
                timestamp(from), timestamp(to));
    }

    @Override
    public List<RunningFundedTotalReportRow> findRunningFundedTotals(Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT id, product_code, funded_at, principal_amount,
                       SUM(principal_amount) OVER (
                           PARTITION BY product_code
                           ORDER BY funded_at, id
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       ) AS running_principal
                FROM loans
                WHERE status = 'FUNDED'
                  AND funded_at >= ?
                  AND funded_at < ?
                ORDER BY product_code, funded_at, id
                """, (resultSet, rowNumber) -> new RunningFundedTotalReportRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("product_code"),
                resultSet.getTimestamp("funded_at").toInstant(),
                resultSet.getBigDecimal("principal_amount"),
                resultSet.getBigDecimal("running_principal")),
                timestamp(from), timestamp(to));
    }

    @Override
    public List<RepaymentAggregateReportRow> findPostedRepaymentAggregates(Instant from, Instant to) {
        return jdbcTemplate.query("""
                WITH repayment_totals AS (
                    SELECT r.id AS repayment_id,
                           r.loan_id,
                           r.amount AS repayment_amount,
                           COALESCE(SUM(ra.principal_amount), 0) AS posted_principal,
                           COALESCE(SUM(ra.interest_amount), 0) AS posted_interest
                    FROM repayments r
                    LEFT JOIN repayment_allocations ra ON ra.repayment_id = r.id
                    WHERE r.status = 'POSTED'
                      AND r.posted_at >= ?
                      AND r.posted_at < ?
                    GROUP BY r.id, r.loan_id, r.amount
                )
                SELECT loan_id,
                       SUM(posted_principal) AS posted_principal,
                       SUM(posted_interest) AS posted_interest,
                       SUM(repayment_amount) AS posted_total
                FROM repayment_totals
                GROUP BY loan_id
                ORDER BY loan_id
                """, (resultSet, rowNumber) -> new RepaymentAggregateReportRow(
                resultSet.getObject("loan_id", UUID.class),
                resultSet.getBigDecimal("posted_principal"),
                resultSet.getBigDecimal("posted_interest"),
                resultSet.getBigDecimal("posted_total")),
                timestamp(from), timestamp(to));
    }

    private static FundedLoanReportRow mapFundedLoan(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FundedLoanReportRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("customer_id"),
                resultSet.getString("product_code"),
                resultSet.getBigDecimal("principal_amount"),
                resultSet.getTimestamp("funded_at").toInstant());
    }

    private static RepaymentReportRow mapRepayment(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp postedAt = resultSet.getTimestamp("posted_at");
        return new RepaymentReportRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("loan_id", UUID.class),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("status"),
                postedAt == null ? null : postedAt.toInstant());
    }

    private static Timestamp timestamp(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("report time range must not be null");
        }
        return Timestamp.from(instant);
    }
}
