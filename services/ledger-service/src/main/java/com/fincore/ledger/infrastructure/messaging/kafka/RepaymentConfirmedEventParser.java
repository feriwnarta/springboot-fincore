package com.fincore.ledger.infrastructure.messaging.kafka;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.ledger.domain.model.RepaymentConfirmedEvent;

import org.springframework.stereotype.Component;

@Component
public class RepaymentConfirmedEventParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<RepaymentConfirmedEvent> parse(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!"RepaymentConfirmed".equals(text(root, "eventType"))) {
                return Optional.empty();
            }
            JsonNode payload = node(root, "payload");
            return Optional.of(new RepaymentConfirmedEvent(
                    UUID.fromString(text(root, "eventId")), UUID.fromString(text(root, "aggregateId")),
                    root.path("aggregateVersion").asLong(0), text(root, "businessReference"),
                    Instant.parse(text(root, "occurredAt")), UUID.fromString(text(root, "correlationId")),
                    UUID.fromString(text(payload, "repaymentId")), decimal(payload, "amount"),
                    decimal(payload, "principalAmount"), decimal(payload, "interestAmount"),
                    text(payload, "currency")));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid RepaymentConfirmed event payload", exception);
        }
    }

    private static BigDecimal decimal(JsonNode parent, String field) {
        String value = text(parent, field);
        return new BigDecimal(value);
    }

    private static JsonNode node(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " must not be missing");
        }
        return value;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = node(parent, field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.textValue();
    }
}
