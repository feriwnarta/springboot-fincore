package com.fincore.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ApiResponseContractTest {

    @Test
    void successResponseContainsDataAndTracingMetadata() {
        ApiMeta meta = new ApiMeta(
                "v1",
                "corr-001",
                "req-001",
                Instant.parse("2026-11-17T03:00:00Z"),
                "/api/v1/loans/loan-001");

        ApiResponse<String> response = ApiResponse.success("approved", meta);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("approved");
        assertThat(response.error()).isNull();
        assertThat(response.meta()).isEqualTo(meta);
    }

    @Test
    void errorResponseContainsStableCodeAndNoData() {
        ApiMeta meta = new ApiMeta(
                "v1",
                "corr-001",
                "req-001",
                Instant.parse("2026-11-17T03:00:00Z"),
                "/api/v1/loans/loan-001");
        ApiError error = new ApiError(
                "VALIDATION_ERROR",
                "Request validation failed",
                java.util.List.of(new FieldViolation("amount", "must be greater than zero")));

        ApiResponse<Void> response = ApiResponse.failure(error, meta);

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.error().details()).singleElement()
                .extracting(FieldViolation::field)
                .isEqualTo("amount");
    }
}
