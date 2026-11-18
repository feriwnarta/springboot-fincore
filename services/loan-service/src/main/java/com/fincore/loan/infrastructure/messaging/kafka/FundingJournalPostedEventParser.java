package com.fincore.loan.infrastructure.messaging.kafka;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.loan.domain.model.FundingJournalPostedEvent;

import org.springframework.stereotype.Component;

@Component
public class FundingJournalPostedEventParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<FundingJournalPostedEvent> parse(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!"FundingJournalPosted".equals(requiredText(root, "eventType"))) {
                return Optional.empty();
            }
            JsonNode payload = requiredNode(root, "payload");
            JsonNode amount = requiredNode(payload, "amount");
            if (!amount.isTextual()) {
                throw new IllegalArgumentException("amount must be serialized as a decimal string");
            }
            return Optional.of(new FundingJournalPostedEvent(
                    UUID.fromString(requiredText(root, "eventId")),
                    UUID.fromString(requiredText(root, "aggregateId")),
                    root.path("aggregateVersion").asLong(0),
                    requiredText(root, "businessReference"),
                    Instant.parse(requiredText(root, "occurredAt")),
                    UUID.fromString(requiredText(root, "correlationId")),
                    UUID.fromString(requiredText(payload, "journalId")),
                    new BigDecimal(amount.textValue()),
                    requiredText(payload, "currency")));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid FundingJournalPosted event payload", exception);
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
