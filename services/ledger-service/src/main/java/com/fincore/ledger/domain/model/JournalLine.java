package com.fincore.ledger.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.fincore.ledger.domain.enums.JournalSide;

public record JournalLine(
        UUID id,
        String accountCode,
        JournalSide side,
        BigDecimal amount,
        String currency) {

    public JournalLine {
        Objects.requireNonNull(id, "id must not be null");
        requireText(accountCode, "accountCode");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        requireText(currency, "currency");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
