package com.fincore.platform.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void eventEnvelopeCarriesSagaTraceAndAggregateVersion() {
        UUID eventId = UUID.fromString("018f0000-0000-7000-8000-000000000001");
        UUID correlationId = UUID.fromString("018f0000-0000-7000-8000-000000000002");
        EventEnvelope<String> envelope = new EventEnvelope<>(
                eventId,
                "LoanFunded",
                1,
                "loan-001",
                4,
                "FUND-001",
                Instant.parse("2026-11-17T03:00:00Z"),
                correlationId,
                null,
                "payload");

        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.aggregateVersion()).isEqualTo(4);
        assertThat(envelope.businessReference()).isEqualTo("FUND-001");
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
    }
}
