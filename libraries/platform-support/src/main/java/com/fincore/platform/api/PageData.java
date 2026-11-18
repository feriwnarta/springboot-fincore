package com.fincore.platform.api;

import java.util.List;

public record PageData<T>(List<T> items, PageMeta page) {

    public PageData {
        items = items == null ? List.of() : List.copyOf(items);
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
    }
}
