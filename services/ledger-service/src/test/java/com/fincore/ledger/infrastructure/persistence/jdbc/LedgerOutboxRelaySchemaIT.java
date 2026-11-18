package com.fincore.ledger.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fincore.ledger.LedgerServiceApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = LedgerServiceApplication.class)
@ActiveProfiles("test")
class LedgerOutboxRelaySchemaIT {

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
        registry.add("spring.kafka.listener.auto-startup", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ledgerOutboxSupportsLeaseBasedRelayClaims() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'ledger_outbox_events'
                """).stream().map(row -> row.get("column_name")).toList();
        var statusConstraint = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'ledger_outbox_events_status_check'
                """, String.class);

        assertThat(columns).contains("claim_token", "claimed_at");
        assertThat(statusConstraint).contains("IN_FLIGHT");
    }

    @Test
    void auditEventsRejectUpdateAndDelete() {
        jdbcTemplate.update("""
                INSERT INTO ledger_audit_events (id, event_id, aggregate_type, aggregate_id, aggregate_version,
                    action, outcome, actor_id, actor_role, correlation_id, business_reference, occurred_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'SavingsAccount', 'acc-1', 1,
                    'OPEN', 'SUCCESS', 'actor-1', 'CUSTOMER', 'corr-1', 'REF-1', now())
                """);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE ledger_audit_events SET outcome = 'TAMPERED'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ledger_audit_events"))
                .hasMessageContaining("append-only");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT outcome FROM ledger_audit_events WHERE business_reference = 'REF-1'", String.class))
                .isEqualTo("SUCCESS");
    }
}
