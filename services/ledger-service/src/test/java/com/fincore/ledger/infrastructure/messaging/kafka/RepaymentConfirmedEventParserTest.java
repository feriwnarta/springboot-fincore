package com.fincore.ledger.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RepaymentConfirmedEventParserTest {

    @Test
    void parsesDecimalStringsAndIgnoresOtherEventTypes() {
        UUID eventId = UUID.randomUUID();
        String payload = "{"
                + "\"eventId\":\"" + eventId + "\",\"eventType\":\"RepaymentConfirmed\","
                + "\"aggregateId\":\"00000000-0000-0000-0000-000000000001\",\"aggregateVersion\":1,"
                + "\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"correlationId\":\"00000000-0000-0000-0000-000000000002\","
                + "\"businessReference\":\"REPAY-1\",\"payload\":{"
                + "\"repaymentId\":\"00000000-0000-0000-0000-000000000003\","
                + "\"currency\":\"IDR\",\"amount\":\"18.0000\","
                + "\"principalAmount\":\"10.0000\",\"interestAmount\":\"8.0000\"}}";

        var event = new RepaymentConfirmedEventParser().parse(payload).orElseThrow();

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("18"));
        assertThat(new RepaymentConfirmedEventParser().parse(payload.replace("RepaymentConfirmed", "LoanFunded")))
                .isEmpty();
    }
}
