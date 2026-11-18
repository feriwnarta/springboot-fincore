package com.fincore.loan.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.fincore.loan.LoanServiceApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = LoanServiceApplication.class)
@Sql(scripts = "/loan-postgres-report-fixture.sql")
class PostgresLoanAnalyticsRepositoryIT {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-01-01T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.4-alpine")
            .withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private JdbcLoanAnalyticsRepository repository;

    @Test
    void runsNativeReportQueriesAgainstPostgreSQLAfterFlywayMigration() {
        var topThree = repository.findTopThreeFundedLoansPerCustomer(FROM, TO);
        var runningTotals = repository.findRunningFundedTotals(FROM, TO);
        var repaymentAggregates = repository.findPostedRepaymentAggregates(FROM, TO);

        assertThat(topThree).hasSize(5);
        assertThat(runningTotals).filteredOn(row -> row.productCode().equals("PERSONAL_FLAT"))
                .extracting(row -> row.runningPrincipal().toPlainString())
                .containsExactly("100.0000", "300.0000", "600.0000", "900.0000");
        assertThat(repaymentAggregates).singleElement()
                .satisfies(row -> assertThat(row.postedTotal()).isEqualByComparingTo("200.0000"));
    }
}
