package com.fincore.platform.api;

public record PageMeta(String nextCursor, boolean hasNext, int limit) {

    public PageMeta {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        if (!hasNext && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor must be null when hasNext is false");
        }
        if (hasNext && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor is required when hasNext is true");
        }
    }
}
