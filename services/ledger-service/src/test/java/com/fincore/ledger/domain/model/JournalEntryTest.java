package com.fincore.ledger.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;

import org.junit.jupiter.api.Test;

class JournalEntryTest {

    @Test
    void acceptsBalancedJournalWithDebitAndCreditLines() {
        assertDoesNotThrow(() -> new JournalEntry(
                UUID.randomUUID(),
                "FUNDING-001",
                JournalStatus.POSTED,
                "IDR",
                List.of(
                        new JournalLine(UUID.randomUUID(), "LOAN_RECEIVABLE", JournalSide.DEBIT,
                                new BigDecimal("100000.0000"), "IDR"),
                        new JournalLine(UUID.randomUUID(), "SIMULATED_SETTLEMENT_CLEARING", JournalSide.CREDIT,
                                new BigDecimal("100000.0000"), "IDR"))));
    }

    @Test
    void rejectsUnbalancedJournal() {
        assertThrows(IllegalArgumentException.class, () -> new JournalEntry(
                UUID.randomUUID(),
                "FUNDING-002",
                JournalStatus.POSTED,
                "IDR",
                List.of(
                        new JournalLine(UUID.randomUUID(), "LOAN_RECEIVABLE", JournalSide.DEBIT,
                                new BigDecimal("100000.0000"), "IDR"),
                        new JournalLine(UUID.randomUUID(), "SIMULATED_SETTLEMENT_CLEARING", JournalSide.CREDIT,
                                new BigDecimal("99999.0000"), "IDR"))));
    }

    @Test
    void rejectsJournalWithoutBothSides() {
        assertThrows(IllegalArgumentException.class, () -> new JournalEntry(
                UUID.randomUUID(),
                "FUNDING-003",
                JournalStatus.POSTED,
                "IDR",
                List.of(new JournalLine(UUID.randomUUID(), "LOAN_RECEIVABLE", JournalSide.DEBIT,
                        new BigDecimal("100000.0000"), "IDR"))));
    }
}
