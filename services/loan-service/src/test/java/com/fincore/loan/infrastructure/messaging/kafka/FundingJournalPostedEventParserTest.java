package com.fincore.loan.infrastructure.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FundingJournalPostedEventParserTest {

    @Test
    void parsesFundingJournalPostedEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        String json = """
                {
                  "eventId":"%s","eventType":"FundingJournalPosted","schemaVersion":1,
                  "aggregateId":"%s","aggregateVersion":2,
                  "occurredAt":"2026-11-17T03:00:00Z","correlationId":"%s",
                  "businessReference":"LOAN-001",
                  "payload":{"journalId":"%s","currency":"IDR","amount":"100.0000"}
                }
                """.formatted(eventId, loanId, correlationId, journalId);

        var event = new FundingJournalPostedEventParser().parse(json).orElseThrow();

        assertEquals(eventId, event.eventId());
        assertEquals(loanId, event.aggregateId());
        assertEquals(journalId, event.journalId());
        assertEquals(new BigDecimal("100.0000"), event.amount());
    }
}
