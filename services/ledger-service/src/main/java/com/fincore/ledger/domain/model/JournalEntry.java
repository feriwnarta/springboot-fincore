package com.fincore.ledger.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;

public record JournalEntry(
        UUID id,
        String businessTransactionReference,
        JournalStatus status,
        String currency,
        List<JournalLine> lines) {

    public JournalEntry {
        Objects.requireNonNull(id, "id must not be null");
        requireText(businessTransactionReference, "businessTransactionReference");
        Objects.requireNonNull(status, "status must not be null");
        requireText(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code");
        }
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("journal must contain at least two lines");
        }

        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        boolean hasDebit = false;
        boolean hasCredit = false;
        for (JournalLine line : lines) {
            if (!currency.equals(line.currency())) {
                throw new IllegalArgumentException("all journal lines must use the journal currency");
            }
            if (line.side() == JournalSide.DEBIT) {
                hasDebit = true;
                debitTotal = debitTotal.add(line.amount());
            } else {
                hasCredit = true;
                creditTotal = creditTotal.add(line.amount());
            }
        }
        if (!hasDebit || !hasCredit) {
            throw new IllegalArgumentException("journal must contain both debit and credit lines");
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException("journal debit and credit totals must be equal");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
