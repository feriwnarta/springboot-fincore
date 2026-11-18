package com.fincore.ledger.api.dto.response;

public record JournalLineResponse(
        String accountCode,
        String side,
        String amount,
        String currency) {
}
