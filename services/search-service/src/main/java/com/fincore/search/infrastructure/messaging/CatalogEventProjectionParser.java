package com.fincore.search.infrastructure.messaging;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class CatalogEventProjectionParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<CatalogEventProjection> parse(String json, Instant recordTimestamp) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            String eventType = text(root, "eventType");
            if (!isSupported(eventType)) {
                return Optional.empty();
            }
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            UUID eventId = root.has("eventId")
                    ? UUID.fromString(text(root, "eventId"))
                    : UUID.nameUUIDFromBytes(json.getBytes(StandardCharsets.UTF_8));
            UUID aggregateId = UUID.fromString(text(root, "aggregateId"));
            long version = root.path("aggregateVersion").asLong(payload.path("aggregateVersion").asLong(1));
            Instant occurredAt = instant(root, "occurredAt").orElse(recordTimestamp);
            if (eventType.equals("LoanProductCreated")) {
                return Optional.of(new CatalogEventProjection(
                        CatalogEventProjection.Type.LOAN_PRODUCT, aggregateId, eventType,
                        text(payload, "code"), text(payload, "interestType"), decimal(payload, "annualRate"),
                        decimal(payload, "minAmount"), decimal(payload, "maxAmount"), payload.path("active").asBoolean(),
                        null, null, null, null, null, version, null,
                        instant(payload, "updatedAt").orElse(occurredAt), occurredAt, eventId));
            }
            return Optional.of(new CatalogEventProjection(
                    CatalogEventProjection.Type.SAVINGS_ACCOUNT, aggregateId, eventType,
                    null, null, null, null, null, false,
                    text(payload, "accountNumber"), text(payload, "customerId"), text(payload, "currency"),
                    text(payload, "status"), decimal(payload, "currentBalance"), version,
                    instant(payload, "createdAt").orElse(occurredAt),
                    instant(payload, "updatedAt").orElse(occurredAt), occurredAt, eventId));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid catalog projection event", exception);
        }
    }

    private static boolean isSupported(String eventType) {
        return eventType.equals("LoanProductCreated")
                || eventType.startsWith("SavingsAccount")
                || eventType.startsWith("SavingsDeposit")
                || eventType.startsWith("SavingsWithdrawal");
    }

    private static BigDecimal decimal(JsonNode parent, String field) {
        return new BigDecimal(text(parent, field));
    }

    private static Optional<Instant> instant(JsonNode parent, String field) {
        return parent.hasNonNull(field) ? Optional.of(Instant.parse(text(parent, field))) : Optional.empty();
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank value");
        }
        return value.asText();
    }
}
