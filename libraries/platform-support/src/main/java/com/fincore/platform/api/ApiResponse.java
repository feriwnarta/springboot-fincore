package com.fincore.platform.api;

public record ApiResponse<T>(boolean success, T data, ApiError error, ApiMeta meta) {

    public ApiResponse {
        if (meta == null) {
            throw new IllegalArgumentException("meta must not be null");
        }
        if (success && error != null) {
            throw new IllegalArgumentException("successful response cannot contain an error");
        }
        if (!success && data != null) {
            throw new IllegalArgumentException("error response cannot contain data");
        }
    }

    public static <T> ApiResponse<T> success(T data, ApiMeta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> failure(ApiError error, ApiMeta meta) {
        return new ApiResponse<>(false, null, error, meta);
    }
}
