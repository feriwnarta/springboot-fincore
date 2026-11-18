package com.fincore.ledger.api.dto.response;

import java.util.List;

public record JournalResponse(
        String id,
        String businessTransactionReference,
        String status,
        String currency,
        List<JournalLineResponse> lines) {

    public JournalResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
