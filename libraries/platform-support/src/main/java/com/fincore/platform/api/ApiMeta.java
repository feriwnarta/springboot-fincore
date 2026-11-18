package com.fincore.platform.api;

import java.time.Instant;
import java.util.Objects;

public record ApiMeta(
        String apiVersion,
        String correlationId,
        String requestId,
        Instant timestamp,
        String path) {

    public ApiMeta {
        requireText(apiVersion, "apiVersion");
        requireText(correlationId, "correlationId");
        requireText(requestId, "requestId");
        Objects.requireNonNull(timestamp, "timestamp");
        requireText(path, "path");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
