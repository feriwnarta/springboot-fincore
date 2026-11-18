package com.fincore.loan.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.fincore.loan.LoanServiceApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = LoanServiceApplication.class)
@ActiveProfiles("test")
@Import(JdbcLoanAnalyticsRepository.class)
@Sql("/loan-analytics-test-schema.sql")
class JdbcLoanAnalyticsRepositoryTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-01-01T00:00:00Z");

    @Autowired
    private JdbcLoanAnalyticsRepository repository;

    @Test
    void executesTopThreeWindowQueryPerCustomer() {
        var result = repository.findTopThreeFundedLoansPerCustomer(FROM, TO);

        assertThat(result).extracting(row -> row.customerId() + ":" + row.rank())
                .containsExactly(
                        "customer-001:1", "customer-001:2",
                        "customer-002:1", "customer-002:2",
                        "customer-003:1");
    }

    @Test
    void executesRunningTotalWindowQueryPerProduct() {
        var result = repository.findRunningFundedTotals(FROM, TO);

        assertThat(result).filteredOn(row -> row.productCode().equals("PERSONAL_FLAT"))
                .extracting(row -> row.runningPrincipal().toPlainString())
                .containsExactly("100.0000", "300.0000", "600.0000", "900.0000");
    }

    @Test
    void executesConditionalRepaymentAggregateWithoutMultiplyingRepaymentAmount() {
        var result = repository.findPostedRepaymentAggregates(FROM, TO);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().postedPrincipal()).isEqualByComparingTo("130.0000");
        assertThat(result.getFirst().postedInterest()).isEqualByComparingTo("70.0000");
        assertThat(result.getFirst().postedTotal()).isEqualByComparingTo("200.0000");
    }
}
