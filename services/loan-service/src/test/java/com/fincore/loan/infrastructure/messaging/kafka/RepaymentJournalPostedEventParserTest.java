package com.fincore.loan.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RepaymentJournalPostedEventParserTest {

    @Test
    void parsesRepaymentJournalAcknowledgement() {
        String payload = "{"
                + "\"eventId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"eventType\":\"RepaymentJournalPosted\",\"schemaVersion\":1,"
                + "\"aggregateId\":\"00000000-0000-0000-0000-000000000002\",\"aggregateVersion\":1,"
                + "\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"correlationId\":\"00000000-0000-0000-0000-000000000003\","
                + "\"businessReference\":\"REPAY-1\",\"payload\":{"
                + "\"journalId\":\"00000000-0000-0000-0000-000000000004\","
                + "\"repaymentId\":\"00000000-0000-0000-0000-000000000005\","
                + "\"amount\":\"18.0000\",\"currency\":\"IDR\"}}";

        var event = new RepaymentJournalPostedEventParser().parse(payload).orElseThrow();

        assertThat(event.repaymentId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assertThat(event.amount()).hasToString("18.0000");
    }
}
