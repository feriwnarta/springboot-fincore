package com.fincore.loan.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fincore.loan.LoanServiceApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = LoanServiceApplication.class)
@ActiveProfiles("test")
@Sql(scripts = "/loan-outbox-relay-test-fixture.sql")
class JdbcLoanOutboxRelayRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-01-01T00:01:00Z");

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
    private JdbcLoanOutboxRelayRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimsPendingRowsInCreatedOrderAndSetsLease() {
        UUID token = UUID.fromString("20000000-0000-0000-0000-000000000001");

        var result = repository.claimBatch(2, NOW, Duration.ofSeconds(30), token);

        assertThat(result).extracting(row -> row.eventId().toString())
                .containsExactly(
                        "00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE event_id = ?", String.class,
                result.getFirst().eventId())).isEqualTo("IN_FLIGHT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT claim_token FROM outbox_events WHERE event_id = ?", UUID.class,
                result.getFirst().eventId())).isEqualTo(token);
    }

    @Test
    void reclaimsExpiredInFlightRows() {
        UUID oldToken = UUID.fromString("20000000-0000-0000-0000-000000000009");
        UUID newToken = UUID.fromString("20000000-0000-0000-0000-000000000003");
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'PUBLISHED'
                WHERE event_id IN (
                    '00000000-0000-0000-0000-000000000001',
                    '00000000-0000-0000-0000-000000000002'
                )
                """);
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'IN_FLIGHT', claim_token = ?, claimed_at = ?
                WHERE event_id = '00000000-0000-0000-0000-000000000003'
                """, oldToken, Timestamp.from(Instant.parse("2025-12-31T23:00:00Z")));

        var result = repository.claimBatch(1, NOW, Duration.ofSeconds(30), newToken);

        assertThat(result).singleElement()
                .extracting(row -> row.eventId().toString())
                .isEqualTo("00000000-0000-0000-0000-000000000003");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT claim_token FROM outbox_events WHERE event_id = ?", UUID.class,
                result.getFirst().eventId())).isEqualTo(newToken);
    }

    @Test
    void staleClaimCannotMarkAnotherRelayAttemptPublished() {
        UUID token = UUID.fromString("20000000-0000-0000-0000-000000000001");
        repository.claimBatch(1, NOW, Duration.ofSeconds(30), token);

        repository.markPublished(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000099"), NOW);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM outbox_events
                WHERE event_id = '00000000-0000-0000-0000-000000000001'
                """, String.class)).isEqualTo("IN_FLIGHT");

        repository.markPublished(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), token, NOW);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM outbox_events
                WHERE event_id = '00000000-0000-0000-0000-000000000001'
                """, String.class)).isEqualTo("PUBLISHED");
    }

    @Test
    void concurrentClaimsDoNotReturnTheSamePendingRow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> repository.claimBatch(
                    1, NOW, Duration.ofSeconds(30),
                    UUID.fromString("20000000-0000-0000-0000-000000000011")));
            var second = executor.submit(() -> repository.claimBatch(
                    1, NOW, Duration.ofSeconds(30),
                    UUID.fromString("20000000-0000-0000-0000-000000000012")));

            List<String> eventIds = List.of(
                    first.get().getFirst().eventId().toString(),
                    second.get().getFirst().eventId().toString());
            assertThat(eventIds).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
    }
}
