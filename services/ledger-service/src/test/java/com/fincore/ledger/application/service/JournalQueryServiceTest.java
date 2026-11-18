package com.fincore.ledger.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;
import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.JournalLine;
import com.fincore.ledger.domain.port.JournalRepository;
import com.fincore.platform.web.ResourceNotFoundException;

import org.junit.jupiter.api.Test;

class JournalQueryServiceTest {

    @Test
    void returnsJournalByBusinessReference() {
        JournalEntry journal = journal("FUNDING-QUERY-001");
        JournalQueryService service = new JournalQueryService(new JournalRepository() {
            @Override
            public Optional<JournalEntry> findByBusinessTransactionReference(String reference) {
                return Optional.of(journal);
            }

            @Override
            public void save(JournalEntry value) {
            }
        });

        JournalEntry result = service.findByBusinessTransactionReference("FUNDING-QUERY-001");

        assertEquals(journal.id(), result.id());
    }

    @Test
    void rejectsUnknownBusinessReference() {
        JournalQueryService service = new JournalQueryService(new JournalRepository() {
            @Override
            public Optional<JournalEntry> findByBusinessTransactionReference(String reference) {
                return Optional.empty();
            }

            @Override
            public void save(JournalEntry value) {
            }
        });

        assertThrows(ResourceNotFoundException.class,
                () -> service.findByBusinessTransactionReference("UNKNOWN"));
    }

    private static JournalEntry journal(String reference) {
        return new JournalEntry(
                UUID.randomUUID(), reference, JournalStatus.POSTED, "IDR", List.of(
                        new JournalLine(UUID.randomUUID(), "A", JournalSide.DEBIT,
                                new BigDecimal("10.0000"), "IDR"),
                        new JournalLine(UUID.randomUUID(), "B", JournalSide.CREDIT,
                                new BigDecimal("10.0000"), "IDR")));
    }
}
