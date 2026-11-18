package com.fincore.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    void auditEventPreservesActorBusinessAndSagaMetadata() {
        AuditEvent event = new AuditEvent(
                UUID.fromString("018f0000-0000-7000-8000-000000000003"),
                "Loan",
                "loan-001",
                4,
                "LOAN_APPROVED",
                "COMMITTED",
                "operator-001",
                "LOAN_APPROVER",
                "corr-001",
                "cause-001",
                "APP-001",
                Map.of("reason", "manual approval"),
                Instant.parse("2026-11-17T03:00:00Z"));

        assertThat(event.action()).isEqualTo("LOAN_APPROVED");
        assertThat(event.outcome()).isEqualTo("COMMITTED");
        assertThat(event.actorId()).isEqualTo("operator-001");
        assertThat(event.businessReference()).isEqualTo("APP-001");
        assertThat(event.metadata()).containsEntry("reason", "manual approval");
    }
}
