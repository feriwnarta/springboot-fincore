package com.fincore.loan.application.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

final class LoanLedgerEventJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private LoanLedgerEventJson() {
    }

    static String serialize(
            UUID eventId, String eventType, UUID aggregateId, long aggregateVersion,
            String businessReference, Instant occurredAt, UUID correlationId, Map<String, String> payload) {
        return MAPPER.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "eventType", eventType,
                "schemaVersion", 1,
                "aggregateId", aggregateId.toString(),
                "aggregateVersion", aggregateVersion,
                "businessReference", businessReference,
                "occurredAt", occurredAt.toString(),
                "correlationId", correlationId.toString(),
                "payload", payload));
    }

    static String serializeObject(Map<String, ?> values) {
        return MAPPER.writeValueAsString(values);
    }
}
