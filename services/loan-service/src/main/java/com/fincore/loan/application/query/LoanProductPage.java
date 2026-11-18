package com.fincore.loan.application.query;

import java.util.List;

import com.fincore.loan.domain.model.LoanProduct;

public record LoanProductPage(List<LoanProduct> items, String nextCursor, boolean hasNext) {

    public LoanProductPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (!hasNext && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor must be null when hasNext is false");
        }
        if (hasNext && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor is required when hasNext is true");
        }
    }
}
