package com.fincore.ledger.infrastructure.messaging.kafka;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.ledger.domain.model.LoanFundedEvent;

import org.springframework.stereotype.Component;

@Component
public class LoanFundedEventParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<LoanFundedEvent> parse(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!"LoanFunded".equals(requiredText(root, "eventType"))) {
                return Optional.empty();
            }
            JsonNode payload = requiredNode(root, "payload");
            JsonNode amount = requiredNode(payload, "principalAmount");
            if (!amount.isTextual()) {
                throw new IllegalArgumentException("principalAmount must be serialized as a decimal string");
            }
            return Optional.of(new LoanFundedEvent(
                    UUID.fromString(requiredText(root, "eventId")),
                    UUID.fromString(requiredText(root, "aggregateId")),
                    root.path("aggregateVersion").asLong(0),
                    requiredText(root, "businessReference"),
                    Instant.parse(requiredText(root, "occurredAt")),
                    UUID.fromString(requiredText(root, "correlationId")),
                    requiredText(payload, "customerId"),
                    requiredText(payload, "productCode"),
                    new java.math.BigDecimal(amount.textValue()),
                    requiredText(payload, "currency")));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid LoanFunded event payload", exception);
        }
    }

    private static JsonNode requiredNode(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " must not be missing");
        }
        return value;
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode value = requiredNode(parent, field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.textValue();
    }
}
