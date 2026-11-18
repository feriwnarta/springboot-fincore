package com.fincore.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fincore.loan.application.query.FundedLoanReportRow;
import com.fincore.loan.application.query.RepaymentAggregateReportRow;
import com.fincore.loan.application.query.RepaymentReportRow;
import com.fincore.loan.application.query.RunningFundedTotalReportRow;
import com.fincore.loan.application.query.TopFundedLoanReportRow;
import com.fincore.loan.application.port.LoanAnalyticsRepository;

import org.junit.jupiter.api.Test;

class LoanAnalyticsServiceTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    void ranksTopCustomersWithBigDecimalTieBreaker() {
        FakeLoanAnalyticsRepository repository = new FakeLoanAnalyticsRepository();
        repository.fundedLoans = List.of(
                funded("customer-b", "100000000000000000.0000"),
                funded("customer-a", "100000000000000000.0000"),
                funded("customer-a", "0.0001"),
                funded("customer-c", "50000000000000000.0000"));

        var result = new LoanAnalyticsService(repository)
                .topCustomersFromBoundedDataset(FROM, TO, 1_000);

        assertThat(result).extracting(row -> row.customerId() + ":" + row.fundedPrincipal())
                .containsExactly(
                        "customer-a:100000000000000000.0001",
                        "customer-b:100000000000000000.0000",
                        "customer-c:50000000000000000.0000");
    }

    @Test
    void filtersNonPostedRepaymentsAndRanksByCompletedAmount() {
        FakeLoanAnalyticsRepository repository = new FakeLoanAnalyticsRepository();
        UUID firstLoan = UUID.randomUUID();
        UUID secondLoan = UUID.randomUUID();
        repository.repayments = List.of(
                new RepaymentReportRow(UUID.randomUUID(), firstLoan, new BigDecimal("150.0000"), "POSTED", FROM),
                new RepaymentReportRow(UUID.randomUUID(), firstLoan, new BigDecimal("50.0000"), "POSTED", FROM),
                new RepaymentReportRow(UUID.randomUUID(), secondLoan, new BigDecimal("999.0000"), "CONFIRMED", FROM));

        var result = new LoanAnalyticsService(repository)
                .topRepaidLoansFromBoundedDataset(FROM, TO, 1_000);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.loanId()).isEqualTo(firstLoan);
            assertThat(row.completedRepaymentAmount()).isEqualByComparingTo("200.0000");
        });
    }

    @Test
    void rejectsUnboundedReportLimit() {
        LoanAnalyticsService service = new LoanAnalyticsService(new FakeLoanAnalyticsRepository());

        assertThatThrownBy(() -> service.topCustomersFromBoundedDataset(FROM, TO, 1_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 1000");
    }

    private static FundedLoanReportRow funded(String customerId, String amount) {
        return new FundedLoanReportRow(
                UUID.randomUUID(), customerId, "PERSONAL", new BigDecimal(amount), FROM);
    }

    private static final class FakeLoanAnalyticsRepository implements LoanAnalyticsRepository {
        private List<FundedLoanReportRow> fundedLoans = List.of();
        private List<RepaymentReportRow> repayments = List.of();

        @Override
        public List<FundedLoanReportRow> findFundedLoans(Instant from, Instant to, int limit) {
            return fundedLoans;
        }

        @Override
        public List<RepaymentReportRow> findRepayments(Instant from, Instant to, int limit) {
            return repayments;
        }

        @Override
        public List<TopFundedLoanReportRow> findTopThreeFundedLoansPerCustomer(Instant from, Instant to) {
            return List.of();
        }

        @Override
        public List<RunningFundedTotalReportRow> findRunningFundedTotals(Instant from, Instant to) {
            return List.of();
        }

        @Override
        public List<RepaymentAggregateReportRow> findPostedRepaymentAggregates(Instant from, Instant to) {
            return List.of();
        }
    }
}
