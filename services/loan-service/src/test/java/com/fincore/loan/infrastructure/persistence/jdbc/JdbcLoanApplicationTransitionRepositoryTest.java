package com.fincore.loan.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fincore.loan.LoanServiceApplication;
import com.fincore.loan.domain.enums.LoanApplicationStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = LoanServiceApplication.class)
@ActiveProfiles("test")
@Import(JdbcLoanApplicationTransitionRepository.class)
@Sql("/loan-application-test-schema.sql")
class JdbcLoanApplicationTransitionRepositoryTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private JdbcLoanApplicationTransitionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void transitionsOnlyWhenTheApplicationIsSubmittedAtTheExpectedVersion() {
        Instant transitionedAt = Instant.parse("2026-11-17T03:05:00Z");

        assertThat(repository.transition(APPLICATION_ID, 1, LoanApplicationStatus.APPROVED, transitionedAt))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM loan_applications WHERE id = ?", String.class, APPLICATION_ID))
                .isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM loan_applications WHERE id = ?", Long.class, APPLICATION_ID))
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approved_at FROM loan_applications WHERE id = ?", Object.class, APPLICATION_ID))
                .isNotNull();

        assertThat(repository.transition(APPLICATION_ID, 1, LoanApplicationStatus.REJECTED, transitionedAt))
                .isFalse();
    }
}
