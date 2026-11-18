package com.fincore.ledger.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fincore.ledger.domain.enums.JournalSide;
import com.fincore.ledger.domain.enums.JournalStatus;
import com.fincore.ledger.domain.model.JournalEntry;
import com.fincore.ledger.domain.model.JournalLine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcJournalRepositoryTest {

    private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("schema.sql")
            .build();
    private final JdbcJournalRepository repository = new JdbcJournalRepository(new JdbcTemplate(database));

    @AfterEach
    void closeDatabase() {
        database.shutdown();
    }

    @Test
    void savesAndFindsJournalWithItsLines() {
        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                "FUNDING-JDBC-001",
                JournalStatus.POSTED,
                "IDR",
                List.of(
                        new JournalLine(UUID.randomUUID(), "LOAN_RECEIVABLE", JournalSide.DEBIT,
                                new BigDecimal("125000.0000"), "IDR"),
                        new JournalLine(UUID.randomUUID(), "SIMULATED_SETTLEMENT_CLEARING", JournalSide.CREDIT,
                                new BigDecimal("125000.0000"), "IDR")));

        repository.save(journal);

        var actual = repository.findByBusinessTransactionReference("FUNDING-JDBC-001");
        assertTrue(actual.isPresent());
        assertEquals(journal.id(), actual.get().id());
        assertEquals(journal.lines().size(), actual.get().lines().size());
        assertEquals(journal.lines().getFirst().amount(), actual.get().lines().getFirst().amount());
    }
}
