package com.fincore.ledger.infrastructure.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LoanFundedEventParserTest {

    private final LoanFundedEventParser parser = new LoanFundedEventParser();

    @Test
    void parsesLoanFundedEnvelopeAndDecimalStringAmount() {
        UUID eventId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "LoanFunded",
                  "schemaVersion": 1,
                  "aggregateId": "%s",
                  "aggregateVersion": 4,
                  "occurredAt": "2026-11-17T03:00:00Z",
                  "correlationId": "%s",
                  "businessReference": "LOAN-FUNDED-001",
                  "payload": {
                    "customerId": "customer-001",
                    "productCode": "STANDARD",
                    "principalAmount": "15000000.0000",
                    "currency": "IDR"
                  }
                }
                """.formatted(eventId, loanId, correlationId);

        var event = parser.parse(payload).orElseThrow();

        assertEquals(eventId, event.eventId());
        assertEquals(loanId, event.aggregateId());
        assertEquals(correlationId, event.correlationId());
        assertEquals(new BigDecimal("15000000.0000"), event.principalAmount());
    }

    @Test
    void ignoresOtherLoanEvents() {
        String payload = """
                {"eventId":"%s","eventType":"LoanApproved"}
                """.formatted(UUID.randomUUID());

        assertTrue(parser.parse(payload).isEmpty());
    }
}
