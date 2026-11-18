package com.fincore.platform.api;

import java.util.List;

public record ApiError(String code, String message, List<FieldViolation> details) {

    public ApiError {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        details = details == null ? List.of() : List.copyOf(details);
    }
}
