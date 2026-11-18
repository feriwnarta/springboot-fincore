package com.fincore.ledger.api.dto.response;

import java.util.List;

public record SavingsStatementResponse(List<SavingsTransactionResponse> transactions, String nextCursor) {
    public SavingsStatementResponse {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }
}
