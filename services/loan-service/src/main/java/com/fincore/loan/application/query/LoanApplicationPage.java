package com.fincore.loan.application.query;

import java.util.List;

import com.fincore.loan.domain.model.LoanApplication;

public record LoanApplicationPage(List<LoanApplication> items, String nextCursor, boolean hasNext) {

    public LoanApplicationPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (!hasNext && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor must be null when hasNext is false");
        }
        if (hasNext && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor is required when hasNext is true");
        }
    }
}
