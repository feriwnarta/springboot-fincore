package com.fincore.loan.infrastructure.messaging.kafka;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.loan.domain.model.RepaymentJournalPostedEvent;

import org.springframework.stereotype.Component;

@Component
public class RepaymentJournalPostedEventParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<RepaymentJournalPostedEvent> parse(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!"RepaymentJournalPosted".equals(text(root, "eventType"))) {
                return Optional.empty();
            }
            JsonNode payload = node(root, "payload");
            return Optional.of(new RepaymentJournalPostedEvent(
                    UUID.fromString(text(root, "eventId")), UUID.fromString(text(root, "aggregateId")),
                    root.path("aggregateVersion").asLong(0), text(root, "businessReference"),
                    Instant.parse(text(root, "occurredAt")), UUID.fromString(text(root, "correlationId")),
                    UUID.fromString(text(payload, "journalId")), UUID.fromString(text(payload, "repaymentId")),
                    new BigDecimal(text(payload, "amount")), text(payload, "currency")));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid RepaymentJournalPosted event payload", exception);
        }
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
