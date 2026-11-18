package com.fincore.search.infrastructure.messaging;

import static com.fincore.search.domain.model.LoanEventProjection.isLifecycleEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.search.domain.model.LoanEventProjection;

import org.springframework.stereotype.Component;

@Component
public class LoanEventProjectionParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<LoanEventProjection> parse(String json, Instant recordTimestamp) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            String eventType = text(root, "eventType");
            if (!isSupported(eventType)) {
                return Optional.empty();
            }
            JsonNode data = root.has("payload") ? root.get("payload") : root;
            String aggregateId = root.has("aggregateId")
                    ? text(root, "aggregateId")
                    : text(data, "applicationId");
            UUID eventId = root.has("eventId")
                    ? UUID.fromString(text(root, "eventId"))
                    : UUID.nameUUIDFromBytes(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Instant occurredAt = root.has("occurredAt")
                    ? Instant.parse(text(root, "occurredAt"))
                    : recordTimestamp;
            Instant createdAt = data.has("createdAt") ? Instant.parse(text(data, "createdAt")) : occurredAt;
            String status = switch (eventType) {
                case "LoanSubmitted" -> "SUBMITTED";
                case "LoanApproved" -> "APPROVED";
                case "LoanFunded" -> "FUNDED";
                case "RepaymentConfirmed" -> "REPAYMENT_CONFIRMED";
                case "RepaymentJournalPosted" -> "REPAYMENT_POSTED";
                default -> eventType;
            };
            return Optional.of(new LoanEventProjection(
                    UUID.fromString(aggregateId),
                    data.hasNonNull("customerId") ? text(data, "customerId") : null,
                    data.has("productCode") ? text(data, "productCode") : "unknown",
                    status,
                    new BigDecimal(data.has("principalAmount") ? text(data, "principalAmount")
                            : text(data, "amount")),
                    data.has("description") ? text(data, "description") : eventType,
                    data.has("tenorMonths") ? data.get("tenorMonths").asInt() : 0,
                    data.has("businessReference") ? text(data, "businessReference") : null,
                    createdAt,
                    root.path("aggregateVersion").asLong(data.path("aggregateVersion").asLong(1)),
                    occurredAt,
                    eventId,
                    eventType));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid loan projection event", exception);
        }
    }

    private static boolean isSupported(String eventType) {
        return isLifecycleEvent(eventType) || eventType.startsWith("Repayment");
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.textValue();
    }
}
